package com.poker.domain.model;

/**
 * Aggregated statistics for a single player, computed on-demand from
 * their {@code HandAction} and {@code PotResult} history.
 *
 * <p>All percentage fields are expressed as fractions in [0.0, 1.0].
 *
 * <dl>
 *   <dt>handsPlayed</dt>     <dd>Finished hands the player participated in</dd>
 *   <dt>vpip</dt>            <dd>Voluntarily put money in preflop (%)</dd>
 *   <dt>pfr</dt>             <dd>Raised preflop (%)</dd>
 *   <dt>threeBetPct</dt>     <dd>Re-raised when facing a preflop raise (%)</dd>
 *   <dt>aggressionFactor</dt><dd>(bets + raises) / calls, postflop</dd>
 *   <dt>wtsdPct</dt>         <dd>Went to showdown after seeing flop (%)</dd>
 *   <dt>wonAtSdPct</dt>      <dd>Won at showdown (%)</dd>
 *   <dt>avgProfitPerHand</dt><dd>Average net chips gained per played hand</dd>
 * </dl>
 */
public record PlayerStats(
    int    handsPlayed,
    double vpip,
    double pfr,
    double threeBetPct,
    double aggressionFactor,
    double wtsdPct,
    double wonAtSdPct,
    double avgProfitPerHand
) {}
