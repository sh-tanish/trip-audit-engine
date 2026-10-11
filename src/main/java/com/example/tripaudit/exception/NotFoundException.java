package com.example.tripaudit.exception;

/**
 * Thrown when a requested resource (e.g. Trip) is not found.
 * Corresponds to HTTP 404.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
