package com.poker.domain.model;

/**
 * Lifecycle states of a poker table.
 *
 * <pre>
 *   WAITING ──► IN_HAND ──► WAITING   (cycles while hands are played)
 *      │                       │
 *      └──────────────────────►CLOSED
 * </pre>
 */
public enum TableStatus {

    /** Table is open; accepting player joins, waiting for a hand to start. */
    WAITING,

    /** A hand is currently in progress; no new joins or seat changes. */
    IN_HAND,

    /** Table has been shut down; no longer accepting activity. */
    CLOSED;

    /** True if new players are allowed to sit down. */
    public boolean acceptsJoins() {
        return this == WAITING;
    }
}
