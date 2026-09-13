package com.wattwise.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/push/send} (solo ADMIN). El envío es a todos los
 * dispositivos registrados por defecto; puede acotarse por {@code token} (un
 * dispositivo concreto) o por {@code userId} (todos los dispositivos del usuario).
 * Precedencia: token &gt; userId &gt; todos.
 */
public class PushSendRequest {

    @NotBlank(message = "El título de la notificación es obligatorio")
    @Size(max = 200, message = "El título no puede superar los 200 caracteres")
    private String title;

    @Size(max = 1000, message = "El cuerpo no puede superar los 1000 caracteres")
    private String body;

    /** Opcional: si se indica, el envío se acota a todos los tokens de este usuario. */
    private Long userId;

    /** Opcional: si se indica, el envío se acota a un único token de dispositivo. */
    private String token;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}