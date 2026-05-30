package com.poker.web.dto;

import com.poker.domain.entity.HandImport;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response for {@code POST /import/hands} and {@code GET /import/hands/{id}}.
 *
 * <p>{@code status} is one of {@code PENDING}, {@code PROCESSING}, {@code DONE},
 * or {@code FAILED}.  On success {@code errorMessage} is {@code null}.
 */
public record HandImportResponse(
    UUID    importId,
    String  filename,
    String  source,
    int     handsParsed,
    int     handsImported,
    String  status,
    String  errorMessage,
    Instant importedAt
) {
    /** Factory: builds from a {@link HandImport} entity. */
    public static HandImportResponse from(HandImport job) {
        return new HandImportResponse(
            job.getId(),
            job.getFilename(),
            job.getSource().name(),
            job.getHandsParsed(),
            job.getHandsImported(),
            job.getStatus().name(),
            job.getErrorMessage(),
            job.getImportedAt()
        );
    }
}
