package com.poker.exception;

/**
 * Thrown when a request is structurally valid but violates a poker domain rule —
 * e.g. joining a full table, acting out of turn, or raising below the minimum.
 * Maps to HTTP 422 Unprocessable Entity in
 * {@link com.poker.web.advice.GlobalExceptionHandler}.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Short machine-readable error code, e.g. {@code "TABLE_FULL"}. */
    public String getCode() {
        return code;
    }
}
