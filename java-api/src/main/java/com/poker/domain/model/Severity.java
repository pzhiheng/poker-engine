package com.poker.domain.model;

/**
 * Urgency level attached to a {@link CoachingSuggestion}.
 *
 * <ul>
 *   <li>{@link #INFO}     — something to be aware of; no immediate action needed</li>
 *   <li>{@link #WARNING}  — a clear leak; worth addressing soon</li>
 *   <li>{@link #CRITICAL} — significant equity lost repeatedly; fix this first</li>
 * </ul>
 */
public enum Severity {
    INFO,
    WARNING,
    CRITICAL
}
