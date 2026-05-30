package com.poker.domain.model;

/**
 * Lifecycle state of a {@link com.poker.domain.entity.HandImport} job.
 */
public enum ImportStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED
}
