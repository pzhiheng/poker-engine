package com.poker.domain.model;

/**
 * A single coaching tip generated from a player's aggregate statistics.
 *
 * @param category  Broad area: {@code "Preflop"}, {@code "Postflop"},
 *                  {@code "Showdown"}, or {@code "Sample Size"}
 * @param title     Short headline shown in the coaching UI
 * @param detail    Full explanation with actionable advice
 * @param severity  How urgently the player should address this
 */
public record CoachingSuggestion(
    String   category,
    String   title,
    String   detail,
    Severity severity
) {}
