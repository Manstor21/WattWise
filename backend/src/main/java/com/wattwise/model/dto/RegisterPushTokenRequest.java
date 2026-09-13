package com.wattwise.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/push-tokens}. El cliente entrega el token FCM
 * generado por su dispositivo y la plataforma (ANDROID por defecto).
 */
public class RegisterPushTokenRequest {

    @NotBlank(message = "El token FCM es obligatorio")
    @Size(max = 512, message = "El token FCM no puede superar los 512 caracteres")
    private String token;

    /** Opcional: plataforma del dispositivo. Valores válidos: ANDROID, IOS, WEB. Por defecto ANDROID. */
    private String platform;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}