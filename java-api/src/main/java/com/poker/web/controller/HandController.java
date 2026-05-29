package com.poker.web.controller;

import com.poker.service.HandService;
import com.poker.web.dto.ActionRequest;
import com.poker.web.dto.ActionResponse;
import com.poker.web.dto.HandResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for hand lifecycle.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /tables/{tableId}/hands}          — start a new hand</li>
 *   <li>{@code POST /hands/{handId}/actions}           — record a player action</li>
 * </ul>
 *
 * <p>All endpoints require a valid JWT.  The authenticated player's UUID is
 * injected via {@link AuthenticationPrincipal}.
 */
@RestController
public class HandController {

    private final HandService handService;

    public HandController(HandService handService) {
        this.handService = handService;
    }

    /**
     * Starts a new hand at the specified table.
     *
     * @return 201 Created with {@link HandResponse}
     */
    @PostMapping("/tables/{tableId}/hands")
    @ResponseStatus(HttpStatus.CREATED)
    public HandResponse startHand(@PathVariable UUID tableId,
                                  @AuthenticationPrincipal UUID playerId) {
        return handService.startHand(tableId, playerId);
    }

    /**
     * Records a player action (fold, check, call, bet, raise, all-in) and
     * returns the updated game state with real-time coaching feedback.
     *
     * @return 200 OK with {@link ActionResponse} including {@code feedback}
     */
    @PostMapping("/hands/{handId}/actions")
    public ActionResponse recordAction(@PathVariable UUID handId,
                                       @Valid @RequestBody ActionRequest req,
                                       @AuthenticationPrincipal UUID playerId) {
        return handService.recordAction(handId, playerId, req);
    }
}
