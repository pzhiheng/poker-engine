package com.poker.web.dto;

import com.poker.domain.model.ActionFeedback;
import com.poker.web.dto.HandResponse.SeatView;

import java.util.List;
import java.util.UUID;

/**
 * Response returned by {@code POST /hands/{id}/actions}.
 *
 * <p>Contains the updated game state <em>after</em> the action was applied,
 * plus an {@link ActionFeedback} object with real-time coaching.
 *
 * @param handId      UUID of the hand
 * @param actionOrder sequence number of this action within the hand
 * @param street      current betting street after the action (may have advanced)
 * @param potChips    total chips in the pot after the action
 * @param nextSeat    seat number of the next player to act (-1 if street/hand is over)
 * @param seats       updated seat views (opponent cards still masked)
 * @param feedback    coaching analysis of the action just taken
 */
public record ActionResponse(
        UUID           handId,
        int            actionOrder,
        String         street,
        int            potChips,
        int            nextSeat,
        List<SeatView> seats,
        ActionFeedback feedback
) {}
