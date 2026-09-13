package com.wattwise.android.data.remote.dto;

/**
 * Cuerpo de {@code POST /api/push-tokens}: registra el token FCM del
 * dispositivo para la sesión actual del backend.
 */
public class RegisterPushTokenRequest {

    private String token;
    private String platform;

    public RegisterPushTokenRequest() {
    }

    public RegisterPushTokenRequest(String token, String platform) {
        this.token = token;
        this.platform = platform;
    }

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