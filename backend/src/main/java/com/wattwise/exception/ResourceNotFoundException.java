package com.wattwise.exception;

/**
 * Se lanza cuando un recurso solicitado (usuario, electrodoméstico, precio) no existe.
 * GlobalExceptionHandler lo asigna al HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
