package com.poker.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response returned by {@code POST /tables/{id}/hands}.
 *
 * <p>The requesting player's hole cards are visible in {@code myHoleCards}.
 * Inside {@code seats}, opponent hole cards are masked as {@code ["**","**"]}
 * while the requesting player's own cards are shown.
 */
public record HandResponse(
        UUID         handId,
        UUID         tableId,
        String       street,
        int          potChips,
        int          dealerSeat,
        int          sbSeat,
        int          bbSeat,
        List<String> boardCards,    // 0 (preflop), 3 (flop), 4 (turn), or 5 (river) cards
        List<String> myHoleCards,   // null if requesting player is not seated
        List<SeatView> seats
) {

    /**
     * A compact seat view returned inside the hand response.
     *
     * @param seatNo      1-based seat position
     * @param playerId    UUID of the player in this seat
     * @param username    display name
     * @param stackChips  current chip stack (after blind deductions)
     * @param holeCards   actual cards for the requesting player; {@code ["**","**"]} for opponents
     * @param folded      whether the player has folded this hand
     * @param allIn       whether the player is all-in
     */
    public record SeatView(
            int          seatNo,
            UUID         playerId,
            String       username,
            int          stackChips,
            List<String> holeCards,
            boolean      folded,
            boolean      allIn
    ) {}
}
