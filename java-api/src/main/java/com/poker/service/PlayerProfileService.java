package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.model.CoachingSuggestion;
import com.poker.domain.model.PlayerProfile;
import com.poker.domain.model.PlayerStats;
import com.poker.domain.model.PlayerType;
import com.poker.domain.model.Severity;
import com.poker.domain.repository.PlayerRepository;
import com.poker.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link PlayerProfile} by combining computed stats with a
 * rule-based player-type classification and coaching suggestions.
 *
 * <p>Classification and suggestion rules are deliberately simple —
 * the goal is actionable feedback for a recreational player, not a
 * full GTO solver.
 */
@Service
@Transactional(readOnly = true)
public class PlayerProfileService {

    // ── Classification thresholds ────────────────────────────────────────────
    private static final int    MIN_HANDS_FOR_CLASSIFICATION = 30;
    private static final double MANIAC_PFR_CUTOFF     = 0.35;
    private static final double FISH_VPIP_CUTOFF      = 0.35;
    private static final double FISH_PFR_CUTOFF       = 0.08;
    private static final double STATION_VPIP_CUTOFF   = 0.30;
    private static final double STATION_PFR_CUTOFF    = 0.12;
    private static final double LAG_VPIP_LO           = 0.26;
    private static final double LAG_VPIP_HI           = 0.35;
    private static final double LAG_PFR_LO            = 0.18;
    private static final double LAG_PFR_HI            = 0.28;
    private static final double TAG_VPIP_LO           = 0.15;
    private static final double TAG_VPIP_HI           = 0.25;
    private static final double TAG_PFR_LO            = 0.14;
    private static final double TAG_PFR_HI            = 0.22;
    private static final double NIT_VPIP_CUTOFF       = 0.15;

    // ── Coaching thresholds ───────────────────────────────────────────────────
    private static final double HIGH_VPIP_THRESHOLD        = 0.35;
    private static final double LOW_PFR_THRESHOLD          = 0.08;
    private static final double LOW_THREE_BET_THRESHOLD    = 0.04;
    private static final double LOW_AGGRESSION_THRESHOLD   = 1.5;
    private static final double HIGH_WTSD_THRESHOLD        = 0.40;
    private static final double LOW_WON_AT_SD_THRESHOLD    = 0.40;

    private final PlayerRepository        playerRepo;
    private final StatsComputationService statsService;

    public PlayerProfileService(PlayerRepository        playerRepo,
                                StatsComputationService statsService) {
        this.playerRepo   = playerRepo;
        this.statsService = statsService;
    }

    /**
     * Builds and returns a full {@link PlayerProfile} for the given player.
     *
     * @param playerId  UUID of the player
     * @return          profile with stats, type classification, and suggestions
     * @throws ResourceNotFoundException if the player does not exist
     */
    public PlayerProfile buildProfile(UUID playerId) {
        Player player = playerRepo.findById(playerId)
            .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        PlayerStats stats       = statsService.compute(playerId);
        PlayerType  playerType  = classify(stats);
        List<CoachingSuggestion> suggestions = generateSuggestions(stats);

        return new PlayerProfile(player.getId(), player.getUsername(),
            stats, playerType, suggestions);
    }

    // ── Classification ────────────────────────────────────────────────────────

    /**
     * Classifies player style from stats.
     * Package-private for unit testing.
     */
    static PlayerType classify(PlayerStats stats) {
        if (stats.handsPlayed() < MIN_HANDS_FOR_CLASSIFICATION) {
            return PlayerType.UNKNOWN;
        }
        if (stats.pfr() > MANIAC_PFR_CUTOFF) {
            return PlayerType.MANIAC;
        }
        if (stats.vpip() > FISH_VPIP_CUTOFF && stats.pfr() < FISH_PFR_CUTOFF) {
            return PlayerType.FISH;
        }
        if (stats.vpip() > STATION_VPIP_CUTOFF && stats.pfr() < STATION_PFR_CUTOFF) {
            return PlayerType.CALLING_STATION;
        }
        if (stats.vpip() >= LAG_VPIP_LO && stats.vpip() <= LAG_VPIP_HI
                && stats.pfr() >= LAG_PFR_LO && stats.pfr() <= LAG_PFR_HI) {
            return PlayerType.LAG;
        }
        if (stats.vpip() >= TAG_VPIP_LO && stats.vpip() <= TAG_VPIP_HI
                && stats.pfr() >= TAG_PFR_LO && stats.pfr() <= TAG_PFR_HI) {
            return PlayerType.TAG;
        }
        if (stats.vpip() < NIT_VPIP_CUTOFF) {
            return PlayerType.NIT;
        }
        // Catch-all: stats are in-range but don't fit a neat box
        return PlayerType.TAG;
    }

    // ── Coaching suggestions ──────────────────────────────────────────────────

    /**
     * Generates a list of ordered coaching suggestions from stats.
     * Package-private for unit testing.
     *
     * <p>When fewer than 30 hands have been played, only a sample-size
     * caveat is returned — all other stats are too noisy to act on.
     */
    static List<CoachingSuggestion> generateSuggestions(PlayerStats stats) {
        List<CoachingSuggestion> list = new ArrayList<>();

        if (stats.handsPlayed() < MIN_HANDS_FOR_CLASSIFICATION) {
            list.add(new CoachingSuggestion(
                "Sample Size",
                "Too few hands for reliable conclusions",
                "You have fewer than 30 hands recorded. Play more before drawing "
                    + "strong conclusions from your stats.",
                Severity.INFO));
            return List.copyOf(list);
        }

        // ── Preflop ───────────────────────────────────────────────────────────

        if (stats.vpip() > HIGH_VPIP_THRESHOLD) {
            list.add(new CoachingSuggestion(
                "Preflop",
                "Playing too many hands",
                "Your VPIP of " + pct(stats.vpip()) + "% is too high. "
                    + "Tighten your range, especially from early position, "
                    + "to avoid building pots out of position with weak holdings.",
                Severity.WARNING));
        }

        if (stats.pfr() < LOW_PFR_THRESHOLD) {
            list.add(new CoachingSuggestion(
                "Preflop",
                "Raise more preflop",
                "Your PFR of " + pct(stats.pfr()) + "% is very low. "
                    + "Raise with your strong hands to build bigger pots and "
                    + "narrow opponents' ranges before the flop.",
                Severity.WARNING));
        }

        if (stats.threeBetPct() < LOW_THREE_BET_THRESHOLD) {
            list.add(new CoachingSuggestion(
                "Preflop",
                "Low 3-bet frequency",
                "Your 3-bet of " + pct(stats.threeBetPct()) + "% is below the "
                    + "baseline. Mix in bluff 3-bets with suited connectors "
                    + "and small pairs to keep opponents guessing.",
                Severity.INFO));
        }

        // ── Postflop ──────────────────────────────────────────────────────────

        if (stats.aggressionFactor() < LOW_AGGRESSION_THRESHOLD) {
            list.add(new CoachingSuggestion(
                "Postflop",
                "Increase postflop aggression",
                "Your aggression factor of " + stats.aggressionFactor()
                    + " means you call more often than you bet or raise. "
                    + "Betting for value and as bluffs is generally more profitable "
                    + "than calling down passively.",
                Severity.WARNING));
        }

        // ── Showdown ──────────────────────────────────────────────────────────

        if (stats.wtsdPct() > HIGH_WTSD_THRESHOLD) {
            list.add(new CoachingSuggestion(
                "Showdown",
                "Going to showdown too often",
                "You reach showdown in " + pct(stats.wtsdPct()) + "% of hands "
                    + "you see the flop. Fold more often when facing aggression "
                    + "on the river to protect your win rate.",
                Severity.WARNING));
        }

        if (stats.wonAtSdPct() < LOW_WON_AT_SD_THRESHOLD) {
            list.add(new CoachingSuggestion(
                "Showdown",
                "Losing most showdowns",
                "You win only " + pct(stats.wonAtSdPct()) + "% of showdowns. "
                    + "You may be calling down with too-weak hands. "
                    + "Tighten your river calling range.",
                Severity.WARNING));
        }

        return List.copyOf(list);
    }

    /** Converts a [0,1] fraction to a rounded percentage string, e.g. 0.2345 → "23.5". */
    private static String pct(double v) {
        return String.valueOf(Math.round(v * 1000.0) / 10.0);
    }
}
