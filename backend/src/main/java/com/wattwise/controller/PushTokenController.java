package com.wattwise.controller;

import com.wattwise.model.dto.PushTokenDto;
import com.wattwise.model.dto.RegisterPushTokenRequest;
import com.wattwise.security.UserPrincipal;
import com.wattwise.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Registro y gestión de los tokens push del dispositivo del usuario autenticado
 * (requiere JWT). Cada operación queda acotada a la identidad del token.
 */
@RestController
@RequestMapping("/api/push-tokens")
@Tag(name = "Push tokens", description = "Registro y gestión de tokens push del dispositivo (JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    public PushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @PostMapping
    @Operation(summary = "Registrar o actualizar el token push FCM del dispositivo",
            description = "Idempotente por token: si el token ya existe (p. ej. reintento o "
                    + "cambio de usuario en el mismo dispositivo) se reasigna al usuario autenticado.")
    public PushTokenDto register(@Valid @RequestBody RegisterPushTokenRequest request,
                                 Authentication authentication) {
        return pushTokenService.register(currentUserId(authentication), request);
    }

    @GetMapping
    @Operation(summary = "Listar los tokens push registrados del usuario autenticado")
    public List<PushTokenDto> list(Authentication authentication) {
        return pushTokenService.listByUserId(currentUserId(authentication));
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja un token push del usuario (404 si no le pertenece)")
    public void delete(@PathVariable String token, Authentication authentication) {
        pushTokenService.deleteByToken(currentUserId(authentication), token);
    }

    private Long currentUserId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getId();
    }
}