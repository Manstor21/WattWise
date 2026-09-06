package com.wattwise.exception;

/**
 * Thrown when the external ESIOS API fails (timeout, non-2xx, parse error).
 * Mapped by GlobalExceptionHandler to HTTP 502 Bad Gateway.
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
