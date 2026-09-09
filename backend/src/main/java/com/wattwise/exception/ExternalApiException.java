package com.wattwise.exception;

/**
 * Se lanza cuando falla la API externa de ESIOS (timeout, respuesta no 2xx, error de
 * parseo). GlobalExceptionHandler lo asigna al HTTP 502 Bad Gateway.
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
