package com.poker.web.dto;

import com.poker.domain.entity.PokerTable;
import com.poker.domain.model.TableStatus;

import java.util.UUID;

/**
 * API representation of a {@link PokerTable}.
 *
 * <p>Constructed via the static factory {@link #from(PokerTable)} so the
 * controller layer never directly couples to the JPA entity.
 */
public record TableResponse(
    UUID        id,
    String      name,
    int         smallBlind,
    int         bigBlind,
    TableStatus status,
    int         seatCount
) {
    /** Map a persisted entity to the response DTO. */
    public static TableResponse from(PokerTable t) {
        return new TableResponse(
            t.getId(),
            t.getName(),
            t.getSmallBlind(),
            t.getBigBlind(),
            t.getStatus(),
            (int) t.getSeats().stream().filter(s -> s.getPlayer() != null).count()
        );
    }
}
