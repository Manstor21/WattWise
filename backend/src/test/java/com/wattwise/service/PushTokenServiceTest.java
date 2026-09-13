package com.wattwise.service;

import com.wattwise.exception.ResourceNotFoundException;
import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.PushTokenDto;
import com.wattwise.model.dto.RegisterPushTokenRequest;
import com.wattwise.model.entity.PushToken;
import com.wattwise.model.entity.User;
import com.wattwise.repository.PushTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    private PushTokenService pushTokenService;

    @BeforeEach
    void setUp() {
        pushTokenService = new PushTokenService(pushTokenRepository);
    }

    private RegisterPushTokenRequest request(String token, String platform) {
        RegisterPushTokenRequest req = new RegisterPushTokenRequest();
        req.setToken(token);
        req.setPlatform(platform);
        return req;
    }

    private PushToken tokenEntity(Long id, Long userId, String token) {
        PushToken pushToken = new PushToken();
        pushToken.setId(id);
        pushToken.setToken(token);
        User user = new User();
        user.setId(userId);
        pushToken.setUser(user);
        return pushToken;
    }

    @Test
    void registerSavesNewToken() {
        when(pushTokenRepository.findFirstByToken("fcm-1")).thenReturn(Optional.empty());
        when(pushTokenRepository.save(any(PushToken.class))).thenAnswer(inv -> {
            PushToken saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        PushTokenDto dto = pushTokenService.register(1L, request("fcm-1", "ANDROID"));

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getToken()).isEqualTo("fcm-1");
        assertThat(dto.getPlatform()).isEqualTo("ANDROID");
        verify(pushTokenRepository).save(any(PushToken.class));
    }

    @Test
    void registerDefaultsToAndroidWhenPlatformBlank() {
        when(pushTokenRepository.findFirstByToken("fcm-2")).thenReturn(Optional.empty());
        when(pushTokenRepository.save(any(PushToken.class))).thenAnswer(inv -> inv.getArgument(0));

        PushTokenDto dto = pushTokenService.register(1L, request("fcm-2", "  "));

        assertThat(dto.getPlatform()).isEqualTo("ANDROID");
    }

    @Test
    void registerReassignsTokenThatBelongsToAnotherUser() {
        PushToken existing = tokenEntity(3L, 99L, "fcm-shared");
        when(pushTokenRepository.findFirstByToken("fcm-shared")).thenReturn(Optional.of(existing));
        when(pushTokenRepository.save(any(PushToken.class))).thenAnswer(inv -> inv.getArgument(0));

        PushTokenDto dto = pushTokenService.register(1L, request("fcm-shared", "IOS"));

        // El token ya existía pero cambia de dueño y de plataforma.
        assertThat(dto.getToken()).isEqualTo("fcm-shared");
        assertThat(dto.getPlatform()).isEqualTo("IOS");
        assertThat(existing.getUser().getId()).isEqualTo(1L);
    }

    @Test
    void registerRejectsBlankToken() {
        assertThatThrownBy(() -> pushTokenService.register(1L, request("   ", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("vacío");
        verify(pushTokenRepository, never()).save(any());
    }

    @Test
    void registerRejectsInvalidPlatform() {
        assertThatThrownBy(() -> pushTokenService.register(1L, request("fcm-x", "FIREFOX")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Plataforma no válida");
    }

    @Test
    void deleteByTokenRemovesOwnedToken() {
        PushToken owned = tokenEntity(5L, 1L, "fcm-owned");
        when(pushTokenRepository.findFirstByToken("fcm-owned")).thenReturn(Optional.of(owned));

        pushTokenService.deleteByToken(1L, "fcm-owned");

        verify(pushTokenRepository).delete(owned);
    }

    @Test
    void deleteByTokenThrowsWhenTokenBelongsToAnotherUser() {
        PushToken other = tokenEntity(6L, 2L, "fcm-other");
        when(pushTokenRepository.findFirstByToken("fcm-other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> pushTokenService.deleteByToken(1L, "fcm-other"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(pushTokenRepository, never()).delete(any());
    }

    @Test
    void deleteByTokenThrowsWhenMissing() {
        when(pushTokenRepository.findFirstByToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pushTokenService.deleteByToken(1L, "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByUserIdReturnsDtos() {
        when(pushTokenRepository.findByUserId(1L)).thenReturn(List.of(
                tokenEntity(7L, 1L, "fcm-a"),
                tokenEntity(8L, 1L, "fcm-b")));

        List<PushTokenDto> dtos = pushTokenService.listByUserId(1L);

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getToken()).isEqualTo("fcm-a");
        assertThat(dtos.get(1).getToken()).isEqualTo("fcm-b");
    }
}