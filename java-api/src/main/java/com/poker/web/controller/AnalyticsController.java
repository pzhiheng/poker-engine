package com.poker.web.controller;

import com.poker.domain.entity.Player;
import com.poker.domain.model.PlayerStats;
import com.poker.domain.repository.PlayerRepository;
import com.poker.exception.ResourceNotFoundException;
import com.poker.service.StatsComputationService;
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
 *   <li>{@code GET /players/{id}/stats} — computed VPIP/PFR/aggression stats</li>
 * </ul>
 *
 * <p>All endpoints are public (no JWT required) — stats are not sensitive
 * and can be viewed by any interested observer.  This keeps the coaching
 * UX frictionless for onboarding.
 */
@RestController
@RequestMapping("/players")
public class AnalyticsController {

    private final PlayerRepository       playerRepo;
    private final StatsComputationService statsService;

    public AnalyticsController(PlayerRepository playerRepo,
                               StatsComputationService statsService) {
        this.playerRepo   = playerRepo;
        this.statsService = statsService;
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
}
