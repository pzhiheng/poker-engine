package com.poker.web.dto;

import com.poker.domain.model.SeatState;

import java.util.List;
import java.util.UUID;

/**
 * Compact table-state snapshot broadcast to all connected STOMP clients on
 * {@code /topic/tables/{tableId}} after every hand start and every action.
 *
 * <p>Hole cards are intentionally excluded — clients must authenticate and
 * call REST to obtain their own cards.  This payload is safe to broadcast
 * without revealing private information.
 */
public record TableEvent(
    UUID         tableId,
    UUID         handId,
    String       street,
    int          potChips,
    int          nextActionSeat,
    List<SeatView> seats
) {
    /** Per-seat state visible to all connected clients. */
    public record SeatView(
        int     seatNo,
        String  username,
        int     stackChips,
        boolean folded,
        boolean allIn
    ) {}

    /** Build a broadcast-safe event from action-response components. */
    public static TableEvent from(UUID tableId, UUID handId, String street,
                                  int potChips, int nextActionSeat,
                                  List<SeatState> seats) {
        var views = seats.stream()
            .map(s -> new SeatView(
                s.seatNo(), s.username(), s.stackChips(), s.folded(), s.allIn()))
            .toList();
        return new TableEvent(tableId, handId, street, potChips, nextActionSeat, views);
    }
}
