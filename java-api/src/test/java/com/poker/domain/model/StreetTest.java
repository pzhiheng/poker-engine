package com.poker.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreetTest {

    @Test
    void preflop_hasNoBoardCards() {
        assertFalse(Street.PREFLOP.hasBoardCards());
    }

    @Test
    void flopOnward_hasBoardCards() {
        assertTrue(Street.FLOP.hasBoardCards());
        assertTrue(Street.TURN.hasBoardCards());
        assertTrue(Street.RIVER.hasBoardCards());
        assertTrue(Street.SHOWDOWN.hasBoardCards());
    }

    @Test
    void equityStreets_correctSubset() {
        assertFalse(Street.PREFLOP.isEquityStreet());
        assertTrue(Street.FLOP.isEquityStreet());
        assertTrue(Street.TURN.isEquityStreet());
        assertTrue(Street.RIVER.isEquityStreet());
        assertFalse(Street.SHOWDOWN.isEquityStreet());
    }

    @Test
    void next_traversesInOrder() {
        assertEquals(Street.FLOP,     Street.PREFLOP.next());
        assertEquals(Street.TURN,     Street.FLOP.next());
        assertEquals(Street.RIVER,    Street.TURN.next());
        assertEquals(Street.SHOWDOWN, Street.RIVER.next());
    }

    @Test
    void next_fromShowdown_throws() {
        assertThrows(IllegalStateException.class, Street.SHOWDOWN::next);
    }
}
