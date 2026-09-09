package com.wattwise.android.data.remote.dto;

/**
 * DTO Gson para el objeto de usuario anidado en un {@link AuthResponse}.
 */
public class UserDto {

    private Long id;
    private String username;
    private String email;
    private String role; // Rol: USER | ADMIN

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}