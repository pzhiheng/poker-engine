package com.poker.web.dto;

import com.poker.domain.entity.TableSeat;

import java.util.UUID;

/**
 * API representation of a {@link TableSeat}.
 *
 * <p>{@code playerId} is nullable — a {@code null} value means the seat is
 * empty (no player assigned yet).
 */
public record SeatResponse(
    UUID    id,
    UUID    tableId,
    UUID    playerId,   // null = empty seat
    int     seatNo,
    int     stackChips,
    boolean sittingOut
) {
    /** Map a persisted entity to the response DTO. */
    public static SeatResponse from(TableSeat s) {
        return new SeatResponse(
            s.getId(),
            s.getTable().getId(),
            s.getPlayer() == null ? null : s.getPlayer().getId(),
            s.getSeatNo(),
            s.getStackChips(),
            s.isSittingOut()
        );
    }
}
