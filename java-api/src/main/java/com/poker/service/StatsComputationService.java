package com.poker.service;

import com.poker.domain.entity.HandAction;
import com.poker.domain.entity.PotResult;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.PlayerStats;
import com.poker.domain.model.Street;
import com.poker.domain.repository.HandActionRepository;
import com.poker.domain.repository.HandRepository;
import com.poker.domain.repository.PlayerRepository;
import com.poker.domain.repository.PotResultRepository;
import com.poker.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes aggregated statistics for a player from their action and pot history.
 *
 * <p>Calculations are intentionally kept simple and transparent for a coaching
 * platform — full GTO solvers are out of scope. All percentage fields are
 * returned as fractions in [0.0, 1.0].
 *
 * <h3>Key definitions</h3>
 * <ul>
 *   <li><b>VPIP</b> — % of finished hands where the player voluntarily put money
 *       in preflop (CALL, RAISE, BET, or ALL_IN on PREFLOP street; excludes
 *       mandatory blind payments).</li>
 *   <li><b>PFR</b>  — % of finished hands where the player raised preflop
 *       (RAISE or BET action on PREFLOP street).</li>
 *   <li><b>3-bet%</b> — % of finished hands where the player posted a 3-bet
 *       (RAISE action on PREFLOP when there was already at least one raise in
 *       the hand before their action).</li>
 *   <li><b>Aggression factor</b> — (bet + raise) / call on postflop streets.
 *       Returns 0.0 when there are no calls (never passive postflop).</li>
 *   <li><b>WTSD%</b> — % of hands (where the player saw the flop) that reached
 *       showdown (River street).</li>
 *   <li><b>W@SD%</b> — % of showdown hands won.</li>
 *   <li><b>Avg profit / hand</b> — total chips won minus total chips invested,
 *       divided by hands played.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class StatsComputationService {

    private final HandActionRepository actionRepo;
    private final HandRepository       handRepo;
    private final PlayerRepository     playerRepo;
    private final PotResultRepository  potRepo;

    public StatsComputationService(HandActionRepository actionRepo,
                                   HandRepository       handRepo,
                                   PlayerRepository     playerRepo,
                                   PotResultRepository  potRepo) {
        this.actionRepo = actionRepo;
        this.handRepo   = handRepo;
        this.playerRepo = playerRepo;
        this.potRepo    = potRepo;
    }

    /**
     * Computes and returns a {@link PlayerStats} snapshot for the given player.
     *
     * @param playerId  the player whose stats are requested
     * @return          a freshly computed {@link PlayerStats} record
     * @throws ResourceNotFoundException if the player does not exist
     */
    public PlayerStats compute(UUID playerId) {
        // Verify player exists
        playerRepo.findById(playerId)
            .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        // ── Totals ────────────────────────────────────────────────────────────

        long handsPlayed = handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED);
        if (handsPlayed == 0) {
            return new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // ── All actions — partitioned by street ───────────────────────────────

        List<HandAction> allActions   = actionRepo.findAllByPlayerId(playerId);
        List<HandAction> preflopActions = allActions.stream()
            .filter(a -> Street.PREFLOP.equals(a.getStreet()))
            .toList();
        List<HandAction> postflopActions = allActions.stream()
            .filter(a -> a.getStreet() != null && !Street.PREFLOP.equals(a.getStreet()))
            .toList();

        // ── VPIP: hands with any voluntary preflop money-in ───────────────────
        // Excludes fold and check (which are zero-cost)
        Set<UUID> vpipHands = preflopActions.stream()
            .filter(a -> a.getActionType().putsMoney())
            .map(a -> a.getHand().getId())
            .collect(Collectors.toSet());
        double vpip = (double) vpipHands.size() / handsPlayed;

        // ── PFR: hands with a preflop raise ────────────────────────────────────
        Set<UUID> pfrHands = preflopActions.stream()
            .filter(a -> a.getActionType() == ActionType.RAISE
                      || a.getActionType() == ActionType.BET)
            .map(a -> a.getHand().getId())
            .collect(Collectors.toSet());
        double pfr = (double) pfrHands.size() / handsPlayed;

        // ── 3-bet%: hands with ≥ 2 raises preflop and this player's last raise
        //   counts as the re-raise.
        //   Heuristic: if the hand has ≥ 2 distinct raisers preflop, the second
        //   one counts as the 3-bettor.
        // Group preflop actions by hand, then check if a hand had ≥ 2 distinct
        // raiser/bet actions and this player raised after at least one other raise.
        Set<UUID> threeBetHands = preflopActions.stream()
            .filter(a -> a.getActionType() == ActionType.RAISE
                      || a.getActionType() == ActionType.BET)
            .filter(a -> {
                // Count how many raises happened in this hand before this action's order
                List<HandAction> priorRaises = preflopActions.stream()
                    .filter(b -> b.getHand().getId().equals(a.getHand().getId())
                              && b.getActionOrder() < a.getActionOrder()
                              && (b.getActionType() == ActionType.RAISE
                                  || b.getActionType() == ActionType.BET))
                    .toList();
                return !priorRaises.isEmpty();
            })
            .map(a -> a.getHand().getId())
            .collect(Collectors.toSet());
        double threeBetPct = (double) threeBetHands.size() / handsPlayed;

        // ── Aggression factor: (bet + raise) / call — postflop only ───────────
        long postBetsAndRaises = postflopActions.stream()
            .filter(a -> a.getActionType() == ActionType.BET
                      || a.getActionType() == ActionType.RAISE)
            .count();
        long postCalls = postflopActions.stream()
            .filter(a -> a.getActionType() == ActionType.CALL)
            .count();
        double aggressionFactor = postCalls == 0 ? 0.0
            : (double) postBetsAndRaises / postCalls;

        // ── WTSD%: saw flop AND reached river ─────────────────────────────────
        List<HandAction> flopActions = allActions.stream()
            .filter(a -> Street.FLOP.equals(a.getStreet()))
            .toList();
        Set<UUID> sawFlop = flopActions.stream()
            .map(a -> a.getHand().getId())
            .collect(Collectors.toSet());

        List<HandAction> riverActions = allActions.stream()
            .filter(a -> Street.RIVER.equals(a.getStreet()))
            .toList();
        Set<UUID> sawRiver = riverActions.stream()
            .map(a -> a.getHand().getId())
            .collect(Collectors.toSet());

        long handsToShowdown = sawFlop.stream().filter(sawRiver::contains).count();
        double wtsdPct = sawFlop.isEmpty() ? 0.0
            : (double) handsToShowdown / sawFlop.size();

        // ── W@SD%: showdown hands won ──────────────────────────────────────────
        long showdownWins = potRepo.countDistinctHandsWonByPlayerId(playerId);
        double wonAtSdPct = handsToShowdown == 0 ? 0.0
            : (double) showdownWins / handsToShowdown;

        // ── Avg profit per hand ────────────────────────────────────────────────
        // Total chips invested = sum of all amount fields across all actions
        int totalInvested = allActions.stream()
            .mapToInt(HandAction::getAmount)
            .sum();
        // Total chips won = sum of chipsAwarded from PotResults
        List<PotResult> wonPots = potRepo.findByWinnerId(playerId);
        int totalWon = wonPots.stream()
            .mapToInt(PotResult::getChipsAwarded)
            .sum();
        double avgProfitPerHand = (double) (totalWon - totalInvested) / handsPlayed;

        return new PlayerStats(
            (int) handsPlayed,
            round(vpip),
            round(pfr),
            round(threeBetPct),
            round(aggressionFactor),
            round(wtsdPct),
            round(wonAtSdPct),
            round(avgProfitPerHand)
        );
    }

    /** Rounds to 4 decimal places for clean output. */
    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
