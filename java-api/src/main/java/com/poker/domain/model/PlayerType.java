package com.poker.domain.model;

/**
 * Broad classification of a player's style derived from their aggregate stats.
 *
 * <p>Ranges are deliberately simplified for a coaching context — full range-matrix
 * analysis is out of scope.
 *
 * <pre>
 * ┌──────────────────┬─────────────────────────────────────────────────────┐
 * │ Type             │ Typical range                                       │
 * ├──────────────────┼─────────────────────────────────────────────────────┤
 * │ UNKNOWN          │ &lt; 30 hands — sample too small to classify           │
 * │ MANIAC           │ PFR &gt; 35% regardless of VPIP                        │
 * │ FISH             │ VPIP &gt; 35%, PFR &lt; 8%   (loose-passive)              │
 * │ CALLING_STATION  │ VPIP &gt; 30%, PFR &lt; 12%  (calls too much)             │
 * │ LAG              │ VPIP 26–35%, PFR 18–28% (loose-aggressive)          │
 * │ TAG              │ VPIP 15–25%, PFR 14–22% (tight-aggressive, ideal)   │
 * │ NIT              │ VPIP &lt; 15% (too tight)                              │
 * └──────────────────┴─────────────────────────────────────────────────────┘
 * </pre>
 */
public enum PlayerType {

    UNKNOWN(
        "Not enough hands to classify — play at least 30 before drawing conclusions."),
    MANIAC(
        "Hyper-aggressive: raising far too often regardless of hand strength."),
    FISH(
        "Loose-passive: playing too many hands without the aggression to back it up."),
    CALLING_STATION(
        "Passive caller: entering too many pots but rarely applying pressure."),
    LAG(
        "Loose-aggressive: wide range with high aggression — profitable but high variance."),
    TAG(
        "Tight-aggressive: solid fundamentals and the baseline to aim for."),
    NIT(
        "Tight-passive: folding too much and missing profitable spots.");

    private final String description;

    PlayerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
