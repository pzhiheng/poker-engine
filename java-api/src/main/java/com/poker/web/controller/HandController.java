package com.poker.web.controller;

import com.poker.service.HandService;
import com.poker.web.dto.HandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for hand lifecycle.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /tables/{id}/hands} — start a new hand at the given table</li>
 * </ul>
 *
 * <p>All endpoints require a valid JWT (see
 * {@link com.poker.config.SecurityConfig}).  The authenticated player's UUID is
 * injected via {@link AuthenticationPrincipal} so the response can show that
 * player's hole cards while masking opponents'.
 */
@RestController
@RequestMapping("/tables/{tableId}/hands")
public class HandController {

    private final HandService handService;

    public HandController(HandService handService) {
        this.handService = handService;
    }

    /**
     * Starts a new hand at the specified table, deals hole cards, and posts blinds.
     *
     * @param tableId  UUID of the table (path variable)
     * @param playerId UUID of the authenticated caller (injected from JWT principal)
     * @return 201 Created with {@link HandResponse}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HandResponse startHand(@PathVariable UUID tableId,
                                  @AuthenticationPrincipal UUID playerId) {
        return handService.startHand(tableId, playerId);
    }
}
