package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.model.CoachingSuggestion;
import com.poker.domain.model.PlayerProfile;
import com.poker.domain.model.PlayerStats;
import com.poker.domain.model.PlayerType;
import com.poker.domain.model.Severity;
import com.poker.domain.repository.PlayerRepository;
import com.poker.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PlayerProfileService}.
 *
 * <p>The static {@code classify} and {@code generateSuggestions} helpers are
 * package-private so they can be tested directly without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerProfileServiceTest {

    @Mock PlayerRepository        playerRepo;
    @Mock StatsComputationService statsService;

    PlayerProfileService service;
    UUID   playerId;
    Player player;

    @BeforeEach
    void setUp() throws Exception {
        service  = new PlayerProfileService(playerRepo, statsService);
        playerId = UUID.randomUUID();
        player   = newPlayer(playerId, "alice", 1_000);
        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
    }

    // ── Player type classification ────────────────────────────────────────────

    @Test
    void classify_insufficientData_returnsUnknown() {
        PlayerStats stats = stats(5, 0.20, 0.15, 0, 2.0, 0.35, 0.50, 10);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.UNKNOWN);
    }

    @Test
    void classify_highPfr_returnsManiac() {
        PlayerStats stats = stats(50, 0.40, 0.38, 0.10, 4.0, 0.30, 0.55, 20);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.MANIAC);
    }

    @Test
    void classify_loosePassive_returnsFish() {
        PlayerStats stats = stats(50, 0.45, 0.05, 0.01, 0.8, 0.45, 0.38, -5);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.FISH);
    }

    @Test
    void classify_highVpipLowPfr_returnsCallingStation() {
        PlayerStats stats = stats(50, 0.38, 0.09, 0.02, 0.5, 0.42, 0.35, -8);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.CALLING_STATION);
    }

    @Test
    void classify_looseAggressive_returnsLag() {
        PlayerStats stats = stats(50, 0.30, 0.22, 0.08, 3.0, 0.36, 0.55, 15);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.LAG);
    }

    @Test
    void classify_tightAggressive_returnsTag() {
        PlayerStats stats = stats(50, 0.20, 0.16, 0.06, 2.2, 0.32, 0.54, 18);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.TAG);
    }

    @Test
    void classify_veryTight_returnsNit() {
        PlayerStats stats = stats(50, 0.10, 0.08, 0.03, 1.8, 0.28, 0.56, 5);
        assertThat(PlayerProfileService.classify(stats)).isEqualTo(PlayerType.NIT);
    }

    // ── Coaching suggestions ──────────────────────────────────────────────────

    @Test
    void suggestions_smallSample_onlyReturnsInfoSuggestion() {
        PlayerStats stats = stats(10, 0.50, 0.02, 0.00, 0.3, 0.50, 0.20, -50);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        assertThat(s).hasSize(1);
        assertThat(s.get(0).category()).isEqualTo("Sample Size");
        assertThat(s.get(0).severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void suggestions_highVpip_includesTightenRangeSuggestion() {
        PlayerStats stats = stats(50, 0.45, 0.16, 0.05, 2.0, 0.30, 0.50, 5);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        assertThat(s).anyMatch(c ->
            c.category().equals("Preflop") && c.severity() == Severity.WARNING
            && c.title().contains("too many hands"));
    }

    @Test
    void suggestions_lowPfr_includesRaisePreflopSuggestion() {
        PlayerStats stats = stats(50, 0.20, 0.05, 0.02, 2.0, 0.30, 0.50, 5);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        assertThat(s).anyMatch(c ->
            c.category().equals("Preflop") && c.severity() == Severity.WARNING
            && c.title().contains("Raise more"));
    }

    @Test
    void suggestions_lowAggression_includesPostflopSuggestion() {
        PlayerStats stats = stats(50, 0.20, 0.16, 0.05, 0.8, 0.30, 0.50, 5);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        assertThat(s).anyMatch(c ->
            c.category().equals("Postflop") && c.severity() == Severity.WARNING);
    }

    @Test
    void suggestions_highWtsd_includesShowdownSuggestion() {
        PlayerStats stats = stats(50, 0.20, 0.16, 0.05, 2.0, 0.48, 0.50, 5);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        assertThat(s).anyMatch(c ->
            c.category().equals("Showdown") && c.title().contains("showdown too often"));
    }

    @Test
    void suggestions_lowWonAtSd_includesLosingShowdownsSuggestion() {
        PlayerStats stats = stats(50, 0.20, 0.16, 0.05, 2.0, 0.30, 0.32, 5);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        assertThat(s).anyMatch(c ->
            c.category().equals("Showdown") && c.title().contains("Losing"));
    }

    @Test
    void suggestions_idealTag_returnsOnlyLowThreeBetInfo() {
        // Solid TAG: VPIP=20%, PFR=16%, 3bet=3% (low), AF=2.2, WTSD=32%, W@SD=54%
        PlayerStats stats = stats(50, 0.20, 0.16, 0.03, 2.2, 0.32, 0.54, 18);
        List<CoachingSuggestion> s = PlayerProfileService.generateSuggestions(stats);

        // Only the 3-bet INFO suggestion fires
        assertThat(s).hasSize(1);
        assertThat(s.get(0).category()).isEqualTo("Preflop");
        assertThat(s.get(0).severity()).isEqualTo(Severity.INFO);
    }

    // ── buildProfile integration ──────────────────────────────────────────────

    @Test
    void buildProfile_returnsFullProfile() {
        PlayerStats stats = stats(50, 0.20, 0.16, 0.05, 2.2, 0.32, 0.54, 18);
        when(statsService.compute(playerId)).thenReturn(stats);

        PlayerProfile profile = service.buildProfile(playerId);

        assertThat(profile.playerId()).isEqualTo(playerId);
        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.playerType()).isEqualTo(PlayerType.TAG);
        assertThat(profile.stats()).isEqualTo(stats);
        assertThat(profile.suggestions()).isNotNull();
    }

    @Test
    void buildProfile_unknownPlayer_throws404() {
        UUID stranger = UUID.randomUUID();
        when(playerRepo.findById(stranger)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.buildProfile(stranger));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PlayerStats stats(int hands, double vpip, double pfr, double threeBet,
                                     double af, double wtsd, double wonSd, double profit) {
        return new PlayerStats(hands, vpip, pfr, threeBet, af, wtsd, wonSd, profit);
    }

    private static Player newPlayer(UUID id, String username, int bankroll) throws Exception {
        Player p = new Player(username, "hash", bankroll);
        var f = Player.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(p, id);
        return p;
    }
}
