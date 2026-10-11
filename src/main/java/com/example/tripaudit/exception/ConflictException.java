package com.example.tripaudit.exception;

/**
 * Thrown when an operation cannot be performed due to the current state of a resource
 * (e.g. attempting to audit a trip that is not yet COMPLETED).
 * Corresponds to HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
