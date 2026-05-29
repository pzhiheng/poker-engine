package com.poker.web.dto;

import com.poker.domain.model.ActionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /hands/{id}/actions}.
 *
 * @param actionType the player's chosen action
 * @param amount     chips committed: 0 for FOLD/CHECK, call amount for CALL,
 *                   total bet/raise size for BET/RAISE, full stack for ALL_IN
 */
public record ActionRequest(
        @NotNull ActionType actionType,
        @Min(0)  int        amount
) {}
