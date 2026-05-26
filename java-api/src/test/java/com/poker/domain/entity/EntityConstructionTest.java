package com.poker.domain.entity;

import com.poker.domain.model.ActionType;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.Street;
import com.poker.domain.model.TableStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests — no Spring context, no database.
 * Verifies entity constructors, derived helpers, and invariant guards.
 */
class EntityConstructionTest {

    // ── Player ────────────────────────────────────────────────────────────────

    @Test
    void playerConstructorSetsFields() {
        var player = new Player("alice", "hash123", 2_000);

        assertEquals("alice",   player.getUsername());
        assertEquals("hash123", player.getPasswordHash());
        assertEquals(2_000,     player.getBankrollChips());
        assertNull(player.getId(),        "id is null until persisted");
        assertNull(player.getCreatedAt(), "createdAt is null until persisted");
    }

    // ── PokerTable ────────────────────────────────────────────────────────────

    @Test
    void pokerTableConstructorSetsFieldsAndDefaultStatus() {
        var table = new PokerTable("High Stakes", 5, 10);

        assertEquals("High Stakes",      table.getName());
        assertEquals(5,                  table.getSmallBlind());
        assertEquals(10,                 table.getBigBlind());
        assertEquals(TableStatus.WAITING, table.getStatus());
        assertTrue(table.getSeats().isEmpty());
        assertTrue(table.getHands().isEmpty());
    }

    @Test
    void pokerTableRejectsMismatchedBlinds() {
        assertThrows(IllegalArgumentException.class,
            () -> new PokerTable("Bad Blinds", 5, 15),
            "BB must equal 2×SB");
    }

    // ── TableSeat ─────────────────────────────────────────────────────────────

    @Test
    void emptySeatIsNotOccupied() {
        var table = new PokerTable("T1", 1, 2);
        var seat  = new TableSeat(table, 3);

        assertFalse(seat.isOccupied());
        assertFalse(seat.isActive());
        assertEquals(3, seat.getSeatNo());
    }

    @Test
    void occupiedSeatIsOccupiedAndActive() {
        var table  = new PokerTable("T2", 1, 2);
        var player = new Player("bob", "hash", 500);
        var seat   = new TableSeat(table, player, 1, 200);

        assertTrue(seat.isOccupied());
        assertTrue(seat.isActive());
        assertEquals(player, seat.getPlayer());
        assertEquals(200,    seat.getStackChips());
    }

    @Test
    void sittingOutPlayerIsNotActive() {
        var table  = new PokerTable("T3", 1, 2);
        var player = new Player("carol", "hash", 500);
        var seat   = new TableSeat(table, player, 2, 100);
        seat.setSittingOut(true);

        assertTrue(seat.isOccupied());
        assertFalse(seat.isActive(), "sitting-out player should not be active");
    }

    // ── Hand ──────────────────────────────────────────────────────────────────

    @Test
    void handConstructorDefaultsToWaitingPreflop() {
        var table = new PokerTable("T4", 2, 4);
        var hand  = new Hand(table, 1);

        assertEquals(Street.PREFLOP,     hand.getStreet());
        assertEquals(HandStatus.WAITING, hand.getStatus());
        assertEquals(0,                  hand.getPotChips());
        assertTrue(hand.isActive());
        assertTrue(hand.getActions().isEmpty());
        assertTrue(hand.getSnapshots().isEmpty());
        assertTrue(hand.getPotResults().isEmpty());
    }

    @Test
    void addToPotAccumulates() {
        var table = new PokerTable("T5", 2, 4);
        var hand  = new Hand(table, 1);
        hand.addToPot(50);
        hand.addToPot(100);
        assertEquals(150, hand.getPotChips());
    }

    @Test
    void finishedHandIsNotActive() {
        var table = new PokerTable("T6", 2, 4);
        var hand  = new Hand(table, 2);
        hand.setStatus(HandStatus.FINISHED);
        assertFalse(hand.isActive());
    }

    // ── HandAction ────────────────────────────────────────────────────────────

    @Test
    void handActionConstructorSetsAllFields() {
        var table  = new PokerTable("T7", 1, 2);
        var hand   = new Hand(table, 1);
        var player = new Player("dave", "hash", 1_000);
        var action = new HandAction(hand, player, ActionType.BET, 40, 3);

        assertEquals(hand,           action.getHand());
        assertEquals(player,         action.getPlayer());
        assertEquals(ActionType.BET, action.getActionType());
        assertEquals(40,             action.getAmount());
        assertEquals(3,              action.getActionOrder());
    }

    @Test
    void foldAndCheckHaveZeroAmount() {
        var table  = new PokerTable("T8", 1, 2);
        var hand   = new Hand(table, 1);
        var player = new Player("eve", "hash", 1_000);

        var fold  = new HandAction(hand, player, ActionType.FOLD,  0, 1);
        var check = new HandAction(hand, player, ActionType.CHECK, 0, 2);

        assertEquals(0, fold.getAmount());
        assertEquals(0, check.getAmount());
    }

    // ── HandSnapshot ──────────────────────────────────────────────────────────

    @Test
    void handSnapshotStoresPayload() {
        var table    = new PokerTable("T9", 2, 4);
        var hand     = new Hand(table, 1);
        var snapshot = new HandSnapshot(hand, 1, "{\"street\":\"PREFLOP\"}");

        assertEquals(1,                        snapshot.getVersionNo());
        assertEquals("{\"street\":\"PREFLOP\"}", snapshot.getPayload());
    }

    // ── PotResult ─────────────────────────────────────────────────────────────

    @Test
    void potResultRecordsWinnerAndReason() {
        var table  = new PokerTable("T10", 5, 10);
        var hand   = new Hand(table, 2);
        var winner = new Player("frank", "hash", 1_000);
        var result = new PotResult(hand, winner, 300, "best hand: flush");

        assertEquals(winner,           result.getWinner());
        assertEquals(300,              result.getChipsAwarded());
        assertEquals("best hand: flush", result.getReason());
    }

    @Test
    void potResultAllowsNullWinner() {
        var table  = new PokerTable("T11", 5, 10);
        var hand   = new Hand(table, 1);
        var result = new PotResult(hand, null, 0, "returned bet");

        assertNull(result.getWinner());
    }
}
