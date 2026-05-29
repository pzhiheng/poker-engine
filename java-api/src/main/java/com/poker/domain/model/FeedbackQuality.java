package com.poker.domain.model;

/**
 * How well the player's action matched the GTO-inspired recommendation.
 *
 * <p>Used in {@link ActionFeedback} to give the player a quick colour-coded
 * signal alongside the textual explanation.
 */
public enum FeedbackQuality {

    /** Player's action matched the recommended action. */
    OPTIMAL,

    /** Slightly off but defensible — equity within ±8 % of the threshold. */
    ACCEPTABLE,

    /** A clearly better option existed. */
    SUBOPTIMAL,

    /**
     * Significant equity was given up — e.g. folding with &gt;50 % equity or
     * calling off with &lt;20 % equity.
     */
    MISTAKE;

    /** Hex colour intended for UI rendering. */
    public String displayColor() {
        return switch (this) {
            case OPTIMAL    -> "#22c55e"; // green-500
            case ACCEPTABLE -> "#eab308"; // yellow-500
            case SUBOPTIMAL -> "#f97316"; // orange-500
            case MISTAKE    -> "#ef4444"; // red-500
        };
    }
}
