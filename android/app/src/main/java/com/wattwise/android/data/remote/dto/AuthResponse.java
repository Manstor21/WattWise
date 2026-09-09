package com.wattwise.android.data.remote.dto;

/**
 * DTO Gson para las respuestas de {@code POST /api/auth/register} y
 * {@code POST /api/auth/login}: el JWT más una vista compacta del usuario.
 */
public class AuthResponse {

    private String token;
    private String tokenType;
    private UserDto user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }
}