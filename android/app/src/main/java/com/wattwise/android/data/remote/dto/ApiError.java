package com.wattwise.android.data.remote.dto;

/**
 * Envoltorio de error estándar devuelto por el backend en respuestas 4xx/5xx:
 * {@code {"timestamp", "status", "error", "message", "path"}}. Se parsea desde
 * {@code Response.errorBody()} para que la UI pueda mostrar el mensaje del servidor.
 */
public class ApiError {

    private long timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}