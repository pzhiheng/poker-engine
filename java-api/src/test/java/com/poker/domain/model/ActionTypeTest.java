package com.poker.domain.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActionTypeTest {

    @Test
    void putsMoney_correctActions() {
        Set<ActionType> expected = EnumSet.of(
            ActionType.CALL, ActionType.BET, ActionType.RAISE, ActionType.ALL_IN);
        for (ActionType at : ActionType.values()) {
            assertEquals(expected.contains(at), at.putsMoney(),
                at + ".putsMoney() was wrong");
        }
    }

    @Test
    void isAggressive_correctActions() {
        Set<ActionType> expected = EnumSet.of(
            ActionType.BET, ActionType.RAISE, ActionType.ALL_IN);
        for (ActionType at : ActionType.values()) {
            assertEquals(expected.contains(at), at.isAggressive(),
                at + ".isAggressive() was wrong");
        }
    }

    @Test
    void fold_neitherAggressiveNorPutsMoney() {
        assertFalse(ActionType.FOLD.putsMoney());
        assertFalse(ActionType.FOLD.isAggressive());
    }

    @Test
    void check_neitherAggressiveNorPutsMoney() {
        assertFalse(ActionType.CHECK.putsMoney());
        assertFalse(ActionType.CHECK.isAggressive());
    }
}
