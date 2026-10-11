package com.example.tripaudit.exception;

/**
 * Thrown when an employee attempts to access or raise SOS for a trip they have no stop on.
 * Corresponds to HTTP 403.
 */
public class ForbiddenTripAccessException extends RuntimeException {
    public ForbiddenTripAccessException(String message) {
        super(message);
    }
}
