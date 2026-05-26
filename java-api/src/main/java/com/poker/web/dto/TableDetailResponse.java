package com.poker.web.dto;

import com.poker.domain.entity.PokerTable;
import com.poker.domain.model.TableStatus;

import java.util.List;
import java.util.UUID;

/**
 * Full detail view of a poker table, including all seats (empty and occupied).
 *
 * <p>Returned by {@code GET /tables/{id}}.  Seats are ordered by
 * {@code seatNo ASC} (enforced by the {@code @OrderBy} on the entity).
 */
public record TableDetailResponse(
    UUID               id,
    String             name,
    int                smallBlind,
    int                bigBlind,
    TableStatus        status,
    List<SeatResponse> seats
) {
    /** Map a persisted entity to the detail DTO. Seats must be initialised. */
    public static TableDetailResponse from(PokerTable t) {
        return new TableDetailResponse(
            t.getId(),
            t.getName(),
            t.getSmallBlind(),
            t.getBigBlind(),
            t.getStatus(),
            t.getSeats().stream().map(SeatResponse::from).toList()
        );
    }
}
