package com.wattwise.config;

import jakarta.annotation.PostConstruct;
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

    /**
     * Fail fast on a missing or weak signing secret.
     *
     * <p>In dev the base {@code application.yml} ships a long demo default;
     * in prod {@code application-prod.yml} maps {@code jwt-secret} to
     * {@code ${JWT_SECRET:}} with no default, so an unset env var becomes
     * {@code null} here and the app refuses to start instead of signing
     * tokens with a guessable key.
     */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "wattwise.security.jwt-secret (env JWT_SECRET) must be set and at least 32 characters long");
        }
    }
}