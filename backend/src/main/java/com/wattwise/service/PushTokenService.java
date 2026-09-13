package com.wattwise.service;

import com.wattwise.exception.ResourceNotFoundException;
import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.PushTokenDto;
import com.wattwise.model.dto.RegisterPushTokenRequest;
import com.wattwise.model.entity.PushToken;
import com.wattwise.model.entity.User;
import com.wattwise.model.enums.Platform;
import com.wattwise.repository.PushTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Registro y gestión de los tokens push FCM del usuario. Un token es único en la
 * tabla: si un dispositivo ya registrado aparece de nuevo (mismo app, otro login),
 * se reasigna al usuario actual. Los tokens de un usuario solo pueden borrarse
 * desde su propio ámbito (el endpoint resuelve como 404 un token ajeno).
 */
@Service
public class PushTokenService {

    private static final String NOT_FOUND = "Token push no encontrado";

    private final PushTokenRepository pushTokenRepository;

    public PushTokenService(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    /** Registra (o actualiza) el token del dispositivo para el usuario autenticado. */
    @Transactional
    public PushTokenDto register(Long userId, RegisterPushTokenRequest request) {
        String token = request.getToken() == null ? null : request.getToken().trim();
        if (token == null || token.isBlank()) {
            throw new ValidationException("El token FCM no puede estar vacío");
        }
        Platform platform = parsePlatform(request.getPlatform());

        PushToken pushToken = pushTokenRepository.findFirstByToken(token).orElseGet(PushToken::new);
        pushToken.setToken(token);
        pushToken.setPlatform(platform);
        User user = new User();
        user.setId(userId);
        pushToken.setUser(user);
        return toDto(pushTokenRepository.save(pushToken));
    }

    /**
     * Da de baja un token del usuario. Solo se elimina si pertenece al usuario
     * autenticado; un token ajeno o inexistente se resuelve igual (404) para no
     * filtrar qué tokens existen.
     */
    @Transactional
    public void deleteByToken(Long userId, String token) {
        PushToken pushToken = pushTokenRepository.findFirstByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!pushToken.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        pushTokenRepository.delete(pushToken);
    }

    @Transactional(readOnly = true)
    public List<PushTokenDto> listByUserId(Long userId) {
        return pushTokenRepository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    private Platform parsePlatform(String raw) {
        if (raw == null || raw.isBlank()) {
            return Platform.ANDROID;
        }
        try {
            return Platform.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Plataforma no válida: " + raw + " (valores válidos: ANDROID, IOS, WEB)");
        }
    }

    private PushTokenDto toDto(PushToken pushToken) {
        PushTokenDto dto = new PushTokenDto();
        dto.setId(pushToken.getId());
        dto.setToken(pushToken.getToken());
        dto.setPlatform(pushToken.getPlatform() != null ? pushToken.getPlatform().name() : null);
        dto.setCreatedAt(pushToken.getCreatedAt());
        return dto;
    }
}