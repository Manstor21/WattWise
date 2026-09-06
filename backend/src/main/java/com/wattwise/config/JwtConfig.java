package com.wattwise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties bound from {@code wattwise.security.*}.
 * {@code secret} maps to env var {@code JWT_SECRET} (see application.yml).
 */
@Component
@ConfigurationProperties(prefix = "wattwise.security")
public class JwtConfig {

    private String jwtSecret;
    private long jwtExpirationMs = 86_400_000L;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    public void setJwtExpirationMs(long jwtExpirationMs) {
        this.jwtExpirationMs = jwtExpirationMs;
    }
}