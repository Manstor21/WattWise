package com.wattwise.exception;

/**
 * Thrown when a requested resource (user, appliance, price) does not exist.
 * Mapped by GlobalExceptionHandler to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
