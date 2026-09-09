package com.wattwise.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración JWT vinculadas desde {@code wattwise.security.*}.
 * {@code secret} se asigna a la variable de entorno {@code JWT_SECRET} (ver application.yml).
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
     * Falla rápido si falta el secreto de firma o es demasiado débil.
     *
     * <p>En dev, el {@code application.yml} base incluye un valor de demostración largo;
     * en prod, {@code application-prod.yml} asigna {@code jwt-secret} a
     * {@code ${JWT_SECRET:}} sin valor por defecto, de modo que una variable de entorno
     * sin definir se convierte en {@code null} aquí y la aplicación se niega a arrancar
     * en lugar de firmar tokens con una clave adivinable.
     */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "wattwise.security.jwt-secret (env JWT_SECRET) must be set and at least 32 characters long");
        }
    }
}