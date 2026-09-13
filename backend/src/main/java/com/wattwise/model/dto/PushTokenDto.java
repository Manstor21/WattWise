package com.wattwise.model.dto;

import java.time.LocalDateTime;

/**
 * Token push registrado del usuario: identidad, token FCM, plataforma y fecha
 * de alta. {@code platform} se serializa como texto (ANDROID | IOS | WEB).
 */
public class PushTokenDto {

    private Long id;

    private String token;

    private String platform;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}