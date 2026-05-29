package com.poker.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Immutable representation of a single seat's state inside a {@link SnapshotPayload}.
 *
 * <p>This is a value object stored as part of the JSON snapshot — it is never
 * persisted on its own.  Hole cards are stored in plain notation (e.g.
 * {@code ["Ah","Kd"]}) but are replaced with {@code ["**","**"]} when sent
 * to players who do not own the seat.
 *
 * <p>{@code streetContribution} tracks how many chips this seat has put into
 * the pot during the current betting street.  It resets to 0 at the start of
 * each new street.  It is used to compute {@code callAmount =
 * currentBet - streetContribution} and therefore the pot odds the player faces.
 */
public record SeatState(
        int          seatNo,
        UUID         playerId,
        String       username,
        int          stackChips,
        List<String> holeCards,           // ["Ah","Kd"] — masked for opponents
        boolean      folded,
        boolean      allIn,
        int          streetContribution,  // chips put in this street (0 at street start)
        boolean      hasActedThisStreet   // true once the player takes a voluntary action this street
) {}
