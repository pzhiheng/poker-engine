package com.poker.domain.model;

/**
 * The four suits in a standard 52-card deck.
 * {@code code} is the single lowercase char used in card notation (e.g. "Ah" → Hearts).
 */
public enum Suit {
    CLUBS('c'),
    DIAMONDS('d'),
    HEARTS('h'),
    SPADES('s');

    private final char code;

    Suit(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    /** Parse a single character — 'c', 'd', 'h', or 's'. */
    public static Suit fromCode(char c) {
        for (Suit s : values()) {
            if (s.code == c) return s;
        }
        throw new IllegalArgumentException("Unknown suit code: '" + c + "'");
    }
}
