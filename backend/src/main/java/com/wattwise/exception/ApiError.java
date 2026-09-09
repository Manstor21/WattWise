package com.wattwise.exception;

/**
 * Envoltorio de error estándar que devuelve la API ante cualquier fallo. Forma
 * consistente en todos los escenarios de error para que los clientes puedan
 * parsear los errores de forma uniforme.
 */
public class ApiError {

    private final long timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    public ApiError(long timestamp, int status, String error, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }
}
