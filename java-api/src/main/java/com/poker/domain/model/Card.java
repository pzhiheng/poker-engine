package com.poker.domain.model;

/**
 * An immutable playing card — a (Rank, Suit) pair.
 *
 * <p>String notation: rank char + suit char, e.g. {@code "Ah"} = Ace of Hearts,
 * {@code "Td"} = Ten of Diamonds, {@code "2c"} = Two of Clubs.
 *
 * <p>Ordering (for display purposes only — hand evaluation uses its own comparator):
 * natural enum ordinal, i.e. TWO < THREE < … < ACE, then CLUBS < … < SPADES.
 */
public record Card(Rank rank, Suit suit) implements Comparable<Card> {

    /** Parse a two-character string such as {@code "Ah"}, {@code "Td"}, {@code "2s"}. */
    public static Card parse(String notation) {
        if (notation == null || notation.length() != 2) {
            throw new IllegalArgumentException(
                "Card notation must be exactly 2 characters, got: '" + notation + "'");
        }
        Rank rank = Rank.fromCode(notation.charAt(0));
        Suit suit = Suit.fromCode(notation.charAt(1));
        return new Card(rank, suit);
    }

    /** Returns the two-character notation, e.g. {@code "Ah"}. */
    @Override
    public String toString() {
        return "" + rank.code() + suit.code();
    }

    /** Natural order: rank first (TWO → ACE), then suit (CLUBS → SPADES). */
    @Override
    public int compareTo(Card other) {
        int cmp = Integer.compare(this.rank.value(), other.rank.value());
        return cmp != 0 ? cmp : this.suit.compareTo(other.suit);
    }
}
