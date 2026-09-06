package com.wattwise.exception;

/**
 * Business/validation exception that maps to HTTP 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
