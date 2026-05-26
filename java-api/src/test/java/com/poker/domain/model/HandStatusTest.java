package com.poker.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandStatusTest {

    @Test
    void waitingIsActive() {
        assertTrue(HandStatus.WAITING.isActive());
    }

    @Test
    void inProgressIsActive() {
        assertTrue(HandStatus.IN_PROGRESS.isActive());
    }

    @Test
    void showdownIsActive() {
        assertTrue(HandStatus.SHOWDOWN.isActive());
    }

    @Test
    void finishedIsNotActive() {
        assertFalse(HandStatus.FINISHED.isActive());
    }

    @Test
    void allValuesPresent() {
        assertEquals(4, HandStatus.values().length,
            "Expected WAITING, IN_PROGRESS, SHOWDOWN, FINISHED");
    }
}
