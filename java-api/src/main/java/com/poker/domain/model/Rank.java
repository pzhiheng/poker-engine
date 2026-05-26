package com.poker.domain.model;

/**
 * Card rank — numeric value used for comparison and hand evaluation.
 * TWO = 2 … ACE = 14 (ace-high only; ace-low straights are handled in the evaluator).
 */
public enum Rank {
    TWO  ('2',  2),
    THREE('3',  3),
    FOUR ('4',  4),
    FIVE ('5',  5),
    SIX  ('6',  6),
    SEVEN('7',  7),
    EIGHT('8',  8),
    NINE ('9',  9),
    TEN  ('T', 10),
    JACK ('J', 11),
    QUEEN('Q', 12),
    KING ('K', 13),
    ACE  ('A', 14);

    private final char code;
    private final int  value;

    Rank(char code, int value) {
        this.code  = code;
        this.value = value;
    }

    public char code()  { return code; }
    public int  value() { return value; }

    /** Parse a single character — '2'–'9', 'T', 'J', 'Q', 'K', 'A'. */
    public static Rank fromCode(char c) {
        for (Rank r : values()) {
            if (r.code == c) return r;
        }
        throw new IllegalArgumentException("Unknown rank code: '" + c + "'");
    }
}
