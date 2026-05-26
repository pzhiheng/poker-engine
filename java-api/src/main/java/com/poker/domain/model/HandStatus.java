package com.poker.domain.model;

/**
 * Lifecycle states of a single poker hand.
 *
 * <pre>
 *   WAITING ──► IN_PROGRESS ──► SHOWDOWN ──► FINISHED
 *                    │                           ▲
 *                    └───────────────────────────┘
 *                        (fold / all-in early finish)
 * </pre>
 */
public enum HandStatus {

    /** Hand object created but cards not yet dealt. */
    WAITING,

    /** Cards dealt; active betting in progress. */
    IN_PROGRESS,

    /** All remaining players are at showdown; hole cards revealed. */
    SHOWDOWN,

    /** Chips awarded; hand complete and immutable. */
    FINISHED;

    /** True if the hand is still in play (not yet finished). */
    public boolean isActive() {
        return this == WAITING || this == IN_PROGRESS || this == SHOWDOWN;
    }
}
