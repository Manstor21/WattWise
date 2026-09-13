package com.wattwise.exception;

/**
 * Se lanza cuando se intenta enviar una notificación push sin que el backend
 * tenga configurado el Admin SDK de Firebase (faltan {@code FIREBASE_SERVICE_ACCOUNT_PATH}
 * y {@code FIREBASE_PROJECT_ID}). Se asigna al HTTP 503 (ver GlobalExceptionHandler).
 */
public class PushNotConfiguredException extends RuntimeException {

    public PushNotConfiguredException(String message) {
        super(message);
    }
}