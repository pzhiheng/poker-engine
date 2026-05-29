package com.poker.web.dto;

import com.poker.domain.model.PlayerStats;

import java.util.UUID;

/**
 * HTTP response payload for {@code GET /players/{id}/stats}.
 *
 * <p>All percentage fields are in [0.0, 1.0]; convert to percentage in the
 * client by multiplying by 100 (e.g. {@code vpip * 100 = 23.4%}).
 */
public record PlayerStatsResponse(
    UUID   playerId,
    String username,
    int    handsPlayed,
    double vpip,
    double pfr,
    double threeBetPct,
    double aggressionFactor,
    double wtsdPct,
    double wonAtSdPct,
    double avgProfitPerHand
) {
    /** Factory method: composes the response from a player identity + computed stats. */
    public static PlayerStatsResponse from(UUID playerId, String username, PlayerStats stats) {
        return new PlayerStatsResponse(
            playerId,
            username,
            stats.handsPlayed(),
            stats.vpip(),
            stats.pfr(),
            stats.threeBetPct(),
            stats.aggressionFactor(),
            stats.wtsdPct(),
            stats.wonAtSdPct(),
            stats.avgProfitPerHand()
        );
    }
}
