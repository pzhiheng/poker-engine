package com.poker.exception;

import java.util.UUID;

/**
 * Thrown when a requested resource does not exist in the database.
 * Maps to HTTP 404 in {@link com.poker.web.advice.GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, UUID id) {
        super(resourceType + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
