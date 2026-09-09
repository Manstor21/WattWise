package com.wattwise.exception;

/**
 * Excepción de negocio/validación que se asigna al HTTP 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
