package com.poker.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CardTest {

    // ── parse: happy path ────────────────────────────────────────────────

    @ParameterizedTest(name = "parse(\"{0}\") → rank={1} suit={2}")
    @CsvSource({
        "Ah, ACE,   HEARTS",
        "Kd, KING,  DIAMONDS",
        "Qc, QUEEN, CLUBS",
        "Js, JACK,  SPADES",
        "Td, TEN,   DIAMONDS",
        "9h, NINE,  HEARTS",
        "2c, TWO,   CLUBS",
    })
    void parse_validNotation(String notation, Rank expectedRank, Suit expectedSuit) {
        Card card = Card.parse(notation);
        assertEquals(expectedRank, card.rank());
        assertEquals(expectedSuit, card.suit());
    }

    @Test
    void parse_roundTrip_toString() {
        String notation = "Ks";
        assertEquals(notation, Card.parse(notation).toString());
    }

    @Test
    void parse_allRanks_allSuits() {
        // Every valid 2-char combination should parse without error
        char[] ranks = {'2','3','4','5','6','7','8','9','T','J','Q','K','A'};
        char[] suits = {'c','d','h','s'};
        for (char r : ranks) {
            for (char s : suits) {
                String notation = "" + r + s;
                Card card = Card.parse(notation);
                assertEquals(notation, card.toString(), "round-trip failed for " + notation);
            }
        }
    }

    // ── parse: error paths ───────────────────────────────────────────────

    @ParameterizedTest(name = "parse(\"{0}\") throws")
    @ValueSource(strings = {"", "A", "Ahh", "Xh", "AX", "1h"})
    void parse_invalidNotation_throws(String bad) {
        assertThrows(IllegalArgumentException.class, () -> Card.parse(bad),
            "Expected IAE for: " + bad);
    }

    @Test
    void parse_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> Card.parse(null));
    }

    // ── compareTo ────────────────────────────────────────────────────────

    @Test
    void compareTo_higherRankIsGreater() {
        assertTrue(Card.parse("Ah").compareTo(Card.parse("Kh")) > 0);
        assertTrue(Card.parse("2s").compareTo(Card.parse("3s")) < 0);
    }

    @Test
    void compareTo_sameRankSameSuit_isEqual() {
        assertEquals(0, Card.parse("Ah").compareTo(Card.parse("Ah")));
    }

    @Test
    void compareTo_sameRankDifferentSuit_orderedBySuit() {
        // CLUBS < DIAMONDS < HEARTS < SPADES (enum ordinal)
        assertTrue(Card.parse("Ac").compareTo(Card.parse("As")) < 0);
    }

    // ── equality (record) ────────────────────────────────────────────────

    @Test
    void equals_sameCard() {
        assertEquals(Card.parse("Td"), Card.parse("Td"));
    }

    @Test
    void equals_differentCard() {
        assertNotEquals(Card.parse("Td"), Card.parse("Th"));
    }
}
