package com.poker.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TableStatusTest {

    @Test
    void waitingAcceptsJoins() {
        assertTrue(TableStatus.WAITING.acceptsJoins());
    }

    @Test
    void inHandDoesNotAcceptJoins() {
        assertFalse(TableStatus.IN_HAND.acceptsJoins());
    }

    @Test
    void closedDoesNotAcceptJoins() {
        assertFalse(TableStatus.CLOSED.acceptsJoins());
    }

    @Test
    void allValuesPresent() {
        assertEquals(3, TableStatus.values().length,
            "Expected WAITING, IN_HAND, CLOSED");
    }
}
