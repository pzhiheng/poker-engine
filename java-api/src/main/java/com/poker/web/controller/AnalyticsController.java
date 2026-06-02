package com.poker.web.controller;

import com.poker.domain.entity.Player;
import com.poker.domain.model.PlayerProfile;
import com.poker.domain.model.PlayerStats;
import com.poker.domain.repository.PlayerRepository;
import com.poker.exception.ResourceNotFoundException;
import com.poker.service.PlayerProfileService;
import com.poker.service.StatsComputationService;
import com.poker.web.dto.PlayerProfileResponse;
import com.poker.web.dto.PlayerStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Coaching analytics endpoints.
 *
 * <ul>
 *   <li>{@code GET /players/{id}/stats}   — raw computed stats (VPIP, PFR, …)</li>
 *   <li>{@code GET /players/{id}/profile} — stats + player-type + coaching tips</li>
 * </ul>
 *
 * <p>All endpoints are public (no JWT required) — stats are not sensitive
 * and keeping them accessible without login reduces onboarding friction.
 */
@Tag(name = "Analytics", description = "Player stats and coaching profile — VPIP, PFR, aggression factor, player type and suggestions")
@SecurityRequirements   // all analytics endpoints are public
@RestController
@RequestMapping("/players")
public class AnalyticsController {

    private final PlayerRepository       playerRepo;
    private final StatsComputationService statsService;
    private final PlayerProfileService    profileService;

    public AnalyticsController(PlayerRepository        playerRepo,
                               StatsComputationService statsService,
                               PlayerProfileService    profileService) {
        this.playerRepo     = playerRepo;
        this.statsService   = statsService;
        this.profileService = profileService;
    }

    @Operation(summary = "Get player stats",
               description = "Computes VPIP, PFR, 3-bet %, aggression factor, WTSD %, and avg profit per hand from the player's full hand history.")
    @ApiResponse(responseCode = "200", description = "Stats computed successfully")
    @ApiResponse(responseCode = "404", description = "Player not found")
    @GetMapping("/{playerId}/stats")
    public ResponseEntity<PlayerStatsResponse> getStats(
            @Parameter(description = "Player UUID") @PathVariable UUID playerId) {
        Player player = playerRepo.findById(playerId)
            .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        PlayerStats stats = statsService.compute(playerId);
        return ResponseEntity.ok(
            PlayerStatsResponse.from(player.getId(), player.getUsername(), stats)
        );
    }

    @Operation(summary = "Get coaching profile",
               description = """
                   Returns stats + player-type classification (TAG / LAG / NIT / CALLING_STATION / FISH / MANIAC)
                   + a ranked list of coaching suggestions (INFO / WARNING / CRITICAL) derived from observed leaks.
                   Requires ≥ 30 hands for a reliable classification; fewer hands returns type UNKNOWN with a sample-size warning.
                   """)
    @ApiResponse(responseCode = "200", description = "Profile built successfully")
    @ApiResponse(responseCode = "404", description = "Player not found")
    @GetMapping("/{playerId}/profile")
    public ResponseEntity<PlayerProfileResponse> getProfile(
            @Parameter(description = "Player UUID") @PathVariable UUID playerId) {
        PlayerProfile profile = profileService.buildProfile(playerId);
        return ResponseEntity.ok(PlayerProfileResponse.from(profile));
    }
}
