package com.poker.service;

import java.util.List;

/**
 * Calculates a player's win equity at a given decision point.
 *
 * <p>Two implementations exist in the codebase:
 * <ol>
 *   <li>{@link StubEquityProvider} — heuristic estimate used until the Go
 *       odds service is wired up (Days 1–18).</li>
 *   <li>{@code GrpcEquityProvider} — delegates to the Go gRPC service for
 *       accurate Monte Carlo / exhaustive equity (added on Day 19).</li>
 * </ol>
 *
 * @see StubEquityProvider
 */
public interface EquityProvider {

    /**
     * Estimates the player's equity (probability of winning) given hole cards,
     * the current community board, and the number of active opponents.
     *
     * @param holeCards    two card notations, e.g. {@code ["Ah","Kh"]}
     * @param boardCards   0, 3, 4, or 5 community card notations
     * @param numOpponents number of non-folded opponents (≥ 1)
     * @return win probability in [0.0, 1.0]
     */
    double calculateEquity(List<String> holeCards,
                           List<String> boardCards,
                           int          numOpponents);
}
