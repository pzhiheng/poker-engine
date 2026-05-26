package com.poker.web.dto;

import java.time.Instant;

/**
 * Standard error envelope returned for all non-2xx responses.
 *
 * <pre>{@code
 * {
 *   "status":    422,
 *   "code":      "TABLE_FULL",
 *   "message":   "Table has no empty seats",
 *   "timestamp": "2026-05-26T10:00:00Z"
 * }
 * }</pre>
 */
public record ErrorResponse(
    int    status,
    String code,
    String message,
    Instant timestamp
) {
    /** Convenience factory — derives timestamp from now. */
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, Instant.now());
    }
}
