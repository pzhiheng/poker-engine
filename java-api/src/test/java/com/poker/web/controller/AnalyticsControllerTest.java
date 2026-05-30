package com.poker.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.entity.Player;
import com.poker.domain.model.CoachingSuggestion;
import com.poker.domain.model.PlayerProfile;
import com.poker.domain.model.PlayerStats;
import com.poker.domain.model.PlayerType;
import com.poker.domain.model.Severity;
import com.poker.domain.repository.PlayerRepository;
import com.poker.security.JwtAuthFilter;
import com.poker.security.JwtService;
import com.poker.service.PlayerProfileService;
import com.poker.service.StatsComputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AnalyticsController}.
 */
@WebMvcTest(AnalyticsController.class)
@Import({
    com.poker.config.SecurityConfig.class,
    com.poker.web.advice.GlobalExceptionHandler.class,
    JwtAuthFilter.class,
    JwtService.class
})
class AnalyticsControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PlayerRepository        playerRepo;
    @MockitoBean StatsComputationService statsService;
    @MockitoBean PlayerProfileService    profileService;

    UUID   playerId;
    Player player;

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        player   = newPlayer(playerId, "alice", 1_000);
    }

    // ── GET /players/{id}/stats ───────────────────────────────────────────────

    @Test
    void getStats_returnsPlayerStatsResponse() throws Exception {
        PlayerStats stats = new PlayerStats(25, 0.28, 0.18, 0.05, 2.3, 0.35, 0.52, 12.4);
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(statsService.compute(playerId)).thenReturn(stats);

        mockMvc.perform(get("/players/{id}/stats", playerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playerId").value(playerId.toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.handsPlayed").value(25))
            .andExpect(jsonPath("$.vpip").value(0.28))
            .andExpect(jsonPath("$.pfr").value(0.18))
            .andExpect(jsonPath("$.aggressionFactor").value(2.3))
            .andExpect(jsonPath("$.wtsdPct").value(0.35))
            .andExpect(jsonPath("$.wonAtSdPct").value(0.52))
            .andExpect(jsonPath("$.avgProfitPerHand").value(12.4));
    }

    @Test
    void getStats_noHandsPlayed_returnsZeroStats() throws Exception {
        PlayerStats empty = new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0);
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(statsService.compute(playerId)).thenReturn(empty);

        mockMvc.perform(get("/players/{id}/stats", playerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.handsPlayed").value(0))
            .andExpect(jsonPath("$.vpip").value(0.0));
    }

    @Test
    void getStats_isPublicEndpoint_noAuthRequired() throws Exception {
        PlayerStats stats = new PlayerStats(5, 0.4, 0.2, 0.06, 1.5, 0.3, 0.5, 5.0);
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(statsService.compute(playerId)).thenReturn(stats);

        mockMvc.perform(get("/players/{id}/stats", playerId))
            .andExpect(status().isOk());
    }

    @Test
    void getStats_unknownPlayer_returns404() throws Exception {
        UUID stranger = UUID.randomUUID();
        when(playerRepo.findById(stranger)).thenReturn(Optional.empty());

        mockMvc.perform(get("/players/{id}/stats", stranger))
            .andExpect(status().isNotFound());
    }

    // ── GET /players/{id}/profile ─────────────────────────────────────────────

    @Test
    void getProfile_returnsPlayerProfileResponse() throws Exception {
        PlayerStats stats = new PlayerStats(50, 0.20, 0.16, 0.05, 2.2, 0.32, 0.54, 18.0);
        List<CoachingSuggestion> suggestions = List.of(
            new CoachingSuggestion("Preflop", "Low 3-bet frequency",
                "Mix in bluff 3-bets.", Severity.INFO)
        );
        PlayerProfile profile = new PlayerProfile(
            playerId, "alice", stats, PlayerType.TAG, suggestions);
        when(profileService.buildProfile(playerId)).thenReturn(profile);

        mockMvc.perform(get("/players/{id}/profile", playerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playerId").value(playerId.toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.playerType").value("TAG"))
            .andExpect(jsonPath("$.playerTypeDescription").isNotEmpty())
            .andExpect(jsonPath("$.stats.handsPlayed").value(50))
            .andExpect(jsonPath("$.suggestions[0].category").value("Preflop"))
            .andExpect(jsonPath("$.suggestions[0].severity").value("INFO"));
    }

    @Test
    void getProfile_isPublicEndpoint_noAuthRequired() throws Exception {
        PlayerStats stats = new PlayerStats(50, 0.20, 0.16, 0.05, 2.2, 0.32, 0.54, 18.0);
        PlayerProfile profile = new PlayerProfile(
            playerId, "alice", stats, PlayerType.TAG, List.of());
        when(profileService.buildProfile(playerId)).thenReturn(profile);

        mockMvc.perform(get("/players/{id}/profile", playerId))
            .andExpect(status().isOk());
    }

    @Test
    void getProfile_unknownPlayer_returns404() throws Exception {
        UUID stranger = UUID.randomUUID();
        when(profileService.buildProfile(stranger))
            .thenThrow(com.poker.exception.ResourceNotFoundException.class);

        mockMvc.perform(get("/players/{id}/profile", stranger))
            .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Player newPlayer(UUID id, String username, int bankroll) throws Exception {
        Player p = new Player(username, "hash", bankroll);
        var f = Player.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(p, id);
        return p;
    }
}
