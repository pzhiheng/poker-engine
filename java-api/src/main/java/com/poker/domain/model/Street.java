package com.poker.domain.model;

/**
 * The betting streets of a Texas Hold'em hand, in order.
 *
 * <p>SHOWDOWN is a terminal pseudo-street — it is reached after RIVER betting
 * closes and indicates that players must reveal their hole cards.
 */
public enum Street {
    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN;

    /** True if community cards have been dealt (flop or later). */
    public boolean hasBoardCards() {
        return this == FLOP || this == TURN || this == RIVER || this == SHOWDOWN;
    }

    /** True if the equity calculation is meaningful (flop/turn/river). */
    public boolean isEquityStreet() {
        return this == FLOP || this == TURN || this == RIVER;
    }

    /** Returns the next street, or throws if already at SHOWDOWN. */
    public Street next() {
        Street[] values = values();
        if (this.ordinal() >= values.length - 1) {
            throw new IllegalStateException("No street after " + this);
        }
        return values[this.ordinal() + 1];
    }
}
