package com.poker.web.controller;

import com.poker.service.HandService;
import com.poker.web.dto.ActionRequest;
import com.poker.web.dto.ActionResponse;
import com.poker.web.dto.HandResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Hands", description = "Hand lifecycle — start hands and record actions with live coaching feedback")
@RestController
public class HandController {

    private final HandService handService;

    public HandController(HandService handService) {
        this.handService = handService;
    }

    @Operation(summary = "Start a hand",
               description = """
                   Deals hole cards to all seated players, posts blinds, and returns
                   the requesting player's hole cards plus masked opponents' cards.
                   The table must be WAITING with 2–6 active seats. **Requires JWT.**
                   """)
    @ApiResponse(responseCode = "201", description = "Hand started — hole cards dealt")
    @ApiResponse(responseCode = "404", description = "Table not found")
    @ApiResponse(responseCode = "422", description = "Business rule violation (hand already active, insufficient players, etc.)")
    @PostMapping("/tables/{tableId}/hands")
    @ResponseStatus(HttpStatus.CREATED)
    public HandResponse startHand(
            @Parameter(description = "Table UUID") @PathVariable UUID tableId,
            @AuthenticationPrincipal UUID playerId) {
        return handService.startHand(tableId, playerId);
    }

    @Operation(summary = "Record a player action",
               description = """
                   Validates and applies a fold / check / call / bet / raise / all-in action.
                   Returns the updated game state **plus real-time coaching feedback**: equity
                   at the decision point, pot odds, recommended action, and a plain-English
                   explanation of whether your action was optimal. **Requires JWT.**
                   It must be the authenticated player's turn.
                   """)
    @ApiResponse(responseCode = "200", description = "Action applied — feedback included in response")
    @ApiResponse(responseCode = "404", description = "Hand not found")
    @ApiResponse(responseCode = "422", description = "Business rule violation (not your turn, illegal action, hand not in progress, etc.)")
    @PostMapping("/hands/{handId}/actions")
    public ActionResponse recordAction(
            @Parameter(description = "Hand UUID") @PathVariable UUID handId,
            @Valid @RequestBody ActionRequest req,
            @AuthenticationPrincipal UUID playerId) {
        return handService.recordAction(handId, playerId, req);
    }
}
