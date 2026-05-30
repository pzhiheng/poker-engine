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

    /**
     * Returns computed statistics for the given player.
     *
     * @param playerId  UUID of the player
     * @return 200 with {@link PlayerStatsResponse}; 404 if not found
     */
    @GetMapping("/{playerId}/stats")
    public ResponseEntity<PlayerStatsResponse> getStats(@PathVariable UUID playerId) {
        Player player = playerRepo.findById(playerId)
            .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        PlayerStats stats = statsService.compute(playerId);
        return ResponseEntity.ok(
            PlayerStatsResponse.from(player.getId(), player.getUsername(), stats)
        );
    }

    /**
     * Returns a full coaching profile: stats, player-type classification,
     * and a list of rule-based coaching suggestions.
     *
     * @param playerId  UUID of the player
     * @return 200 with {@link PlayerProfileResponse}; 404 if not found
     */
    @GetMapping("/{playerId}/profile")
    public ResponseEntity<PlayerProfileResponse> getProfile(@PathVariable UUID playerId) {
        PlayerProfile profile = profileService.buildProfile(playerId);
        return ResponseEntity.ok(PlayerProfileResponse.from(profile));
    }
}
