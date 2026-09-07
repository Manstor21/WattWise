package com.wattwise.android.data.remote.dto;

/**
 * Gson DTO for {@code POST /api/auth/register} and {@code POST /api/auth/login}
 * responses: the JWT plus a compact user view.
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