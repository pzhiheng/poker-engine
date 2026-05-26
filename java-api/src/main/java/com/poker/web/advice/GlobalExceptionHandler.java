package com.poker.web.advice;

import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates exceptions into the standard {@link ErrorResponse} JSON envelope.
 *
 * <p>All handlers log at the appropriate severity:
 * <ul>
 *   <li>400/404/422 — {@code WARN} (client error, not our bug)</li>
 *   <li>500          — {@code ERROR} with full stack trace</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request — bean-validation failure ──────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return ErrorResponse.of(400, "VALIDATION_ERROR", details);
    }

    // ── 400 Bad Request — programmatic argument error ──────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ErrorResponse.of(400, "BAD_REQUEST", ex.getMessage());
    }

    // ── 404 Not Found ──────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ErrorResponse.of(404, "NOT_FOUND", ex.getMessage());
    }

    // ── 422 Unprocessable Entity — domain rule violation ───────────────────

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violated [{}]: {}", ex.getCode(), ex.getMessage());
        return ErrorResponse.of(422, ex.getCode(), ex.getMessage());
    }

    // ── 500 Internal Server Error — unexpected ─────────────────────────────

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ErrorResponse.of(500, "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later.");
    }
}
