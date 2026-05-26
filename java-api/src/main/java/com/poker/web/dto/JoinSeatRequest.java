package com.poker.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /tables/{id}/seats}.
 *
 * <p>The player identified by {@code playerId} takes seat {@code seatNo} at the
 * table, buying in for {@code buyIn} chips deducted from their bankroll.
 */
public record JoinSeatRequest(

    @NotNull(message = "playerId is required")
    UUID playerId,

    @Min(value = 1, message = "seatNo must be between 1 and 6")
    @Max(value = 6, message = "seatNo must be between 1 and 6")
    int seatNo,

    @Min(value = 1, message = "buyIn must be at least 1 chip")
    int buyIn
) {}
