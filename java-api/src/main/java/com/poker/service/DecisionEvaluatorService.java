package com.poker.service;

import com.poker.domain.model.ActionFeedback;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.FeedbackQuality;
import com.poker.domain.model.SeatState;
import com.poker.domain.model.SnapshotPayload;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Produces real-time coaching feedback for a player's action.
 *
 * <p>Given the snapshot state <em>before</em> the action was applied (so equity
 * is computed at the decision point) and the action the player chose, this service:
 * <ol>
 *   <li>Calls {@link EquityProvider} for the player's win probability</li>
 *   <li>Computes pot odds from the call amount relative to the pot</li>
 *   <li>Derives the GTO-inspired recommended action from heuristic rules</li>
 *   <li>Compares actual vs. recommended to assign {@link FeedbackQuality}</li>
 *   <li>Builds a plain-English coaching explanation</li>
 * </ol>
 *
 * <h3>Decision heuristic rules</h3>
 * <pre>
 * Equity &gt; 60%, no bet facing               → BET (value)
 * Equity &gt; 55%, bet facing, equity &gt; potOdds → RAISE
 * potOdds ≤ equity ≤ potOdds + 15%           → CALL
 * Equity &lt; potOdds                            → FOLD
 * Equity &lt; 20%, any bet facing               → FOLD (override)
 * </pre>
 */
@Service
public class DecisionEvaluatorService {

    private static final double RAISE_THRESHOLD     = 0.55;
    private static final double BET_THRESHOLD       = 0.60;
    private static final double CALL_MARGIN         = 0.15;
    private static final double MISTAKE_FOLD_CUTOFF = 0.50; // folding above this is a Mistake
    private static final double MISTAKE_CALL_CUTOFF = 0.20; // calling below this is a Mistake

    private final EquityProvider equityProvider;

    public DecisionEvaluatorService(EquityProvider equityProvider) {
        this.equityProvider = equityProvider;
    }

    /**
     * Evaluates the action and returns coaching feedback.
     *
     * @param actingSeat  the seat that just acted (pre-action state)
     * @param snapshot    the full hand snapshot before the action was applied
     * @param actionTaken the action the player chose
     * @param callAmount  chips needed to call (0 = no bet facing)
     */
    public ActionFeedback evaluate(SeatState       actingSeat,
                                   SnapshotPayload snapshot,
                                   ActionType      actionTaken,
                                   int             callAmount) {

        // ── 1. Count active opponents ─────────────────────────────────────────
        long opponents = snapshot.seats().stream()
            .filter(s -> !s.folded())
            .filter(s -> s.seatNo() != actingSeat.seatNo())
            .count();
        int numOpponents = (int) Math.max(1, opponents);

        // ── 2. Compute equity ─────────────────────────────────────────────────
        double equity = equityProvider.calculateEquity(
            actingSeat.holeCards(),
            snapshot.board(),
            numOpponents
        );

        // ── 3. Compute pot odds ───────────────────────────────────────────────
        double potOdds = 0.0;
        if (callAmount > 0) {
            potOdds = (double) callAmount / (snapshot.pot() + callAmount);
        }

        // ── 4. Derive recommended action ──────────────────────────────────────
        ActionType recommended = recommend(equity, potOdds, callAmount);

        // ── 5. Assess quality ─────────────────────────────────────────────────
        FeedbackQuality quality = assess(actionTaken, recommended, equity, potOdds, callAmount);

        // ── 6. Build explanation ──────────────────────────────────────────────
        String explanation = explain(actionTaken, recommended, equity, potOdds, quality, callAmount);

        // ── 7. Build GTO reasoning bullets ───────────────────────────────────
        List<String> gtoPoints = buildGtoPoints(actionTaken, recommended, equity, potOdds, callAmount);

        return new ActionFeedback(actionTaken, recommended, equity, potOdds, quality, explanation, gtoPoints);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ActionType recommend(double equity, double potOdds, int callAmount) {
        boolean betFacing = callAmount > 0;

        // Override: very weak hand vs any bet → fold
        if (betFacing && equity < MISTAKE_CALL_CUTOFF) {
            return ActionType.FOLD;
        }

        if (!betFacing) {
            // No bet in front: bet for value if strong, else check
            return equity > BET_THRESHOLD ? ActionType.BET : ActionType.CHECK;
        }

        // Bet facing
        if (equity > RAISE_THRESHOLD && equity > potOdds) {
            return ActionType.RAISE;
        }
        if (equity >= potOdds && equity <= potOdds + CALL_MARGIN) {
            return ActionType.CALL;
        }
        if (equity > potOdds) {
            // Equity clearly beats pot odds → call at minimum
            return ActionType.CALL;
        }
        return ActionType.FOLD;
    }

    private FeedbackQuality assess(ActionType actual, ActionType recommended,
                                   double equity, double potOdds, int callAmount) {
        if (actual == recommended) {
            return FeedbackQuality.OPTIMAL;
        }

        // Catastrophic mistakes
        if (actual == ActionType.FOLD && equity > MISTAKE_FOLD_CUTOFF) {
            return FeedbackQuality.MISTAKE;  // folded with majority equity
        }
        if ((actual == ActionType.CALL || actual == ActionType.RAISE)
                && callAmount > 0 && equity < MISTAKE_CALL_CUTOFF) {
            return FeedbackQuality.MISTAKE;  // called/raised with very weak hand
        }

        // Acceptable vs suboptimal: was the equity close to the decision boundary?
        double gap = Math.abs(equity - (potOdds + CALL_MARGIN / 2));
        if (gap <= 0.08) {
            return FeedbackQuality.ACCEPTABLE;
        }
        return FeedbackQuality.SUBOPTIMAL;
    }

    private String explain(ActionType actual, ActionType recommended,
                           double equity, double potOdds,
                           FeedbackQuality quality, int callAmount) {

        String equityPct  = pct(equity);
        String oddsPct    = pct(potOdds);

        return switch (quality) {
            case OPTIMAL -> switch (actual) {
                case FOLD  -> "Good fold. With only " + equityPct + " equity you were right to let it go.";
                case CHECK -> "Good check. Your " + equityPct + " equity doesn't justify a bet here.";
                case CALL  -> "Good call. Your " + equityPct + " equity beats the " + oddsPct + " pot odds.";
                case BET   -> "Good bet. Betting for value with " + equityPct + " equity makes sense.";
                case RAISE -> "Good raise. You had " + equityPct + " equity and only needed " + oddsPct + " — raising for value was the right move.";
                case ALL_IN -> "Reasonable all-in with " + equityPct + " equity.";
            };

            case ACCEPTABLE -> "Reasonable play. You had " + equityPct + " equity"
                + (callAmount > 0 ? " vs " + oddsPct + " pot odds" : "")
                + ". " + recommendationHint(recommended) + " would also have been fine.";

            case SUBOPTIMAL -> "You had " + equityPct + " equity"
                + (callAmount > 0 ? " vs " + oddsPct + " pot odds" : "")
                + ". " + recommendationHint(recommended) + " would have been the stronger play.";

            case MISTAKE -> actual == ActionType.FOLD
                ? "Tough fold — you gave up " + equityPct + " equity. Consider calling or raising when you're ahead."
                : "Risky play with only " + equityPct + " equity and " + oddsPct + " pot odds. Folding preserves more chips.";
        };
    }

    private static String recommendationHint(ActionType rec) {
        return switch (rec) {
            case FOLD  -> "Folding";
            case CHECK -> "Checking";
            case CALL  -> "Calling";
            case BET   -> "Betting for value";
            case RAISE -> "Raising for value";
            case ALL_IN -> "Going all-in";
        };
    }

    private List<String> buildGtoPoints(ActionType actual, ActionType recommended,
                                         double equity, double potOdds, int callAmount) {
        String eq  = pct(equity);
        String po  = pct(potOdds);

        // Reinforcement when the player made the correct play
        if (actual == recommended) {
            return switch (actual) {
                case FOLD  -> List.of(
                    "Folding with " + eq + " equity is correct — you were a significant underdog.",
                    "Pot odds math: you needed more than " + po + " equity to call profitably, but had less.",
                    "Disciplined folds protect your stack for better spots.");
                case CHECK -> List.of(
                    "Checking is right here — your " + eq + " equity doesn't justify a bet.",
                    "Betting a weak hand turns it into a bluff with little fold equity.",
                    "Pot control is a core GTO concept: don't bloat pots when behind.");
                case CALL  -> List.of(
                    "Good call — your " + eq + " equity exceeds the " + po + " pot odds required.",
                    "Pot odds math: you break even at " + po + "; anything above is profitable long-term.",
                    "Calling with a positive equity edge is always the right mathematical play.");
                case BET   -> List.of(
                    "Betting for value with " + eq + " equity builds the pot while you're ahead.",
                    "GTO principle: bet the top of your range to extract chips from weaker hands.",
                    "Value betting polarises your range and makes you harder to read.");
                case RAISE -> List.of(
                    "Raising extracts maximum value from your " + eq + " equity advantage.",
                    "You only needed " + po + " to call, but raising builds a bigger pot while ahead.",
                    "GTO raises with strong hands to charge drawing hands and build value.");
                case ALL_IN -> List.of(
                    "Going all-in with " + eq + " equity commits chips at a mathematical advantage.",
                    "When equity far exceeds pot odds, getting all the money in is optimal.");
            };
        }

        // Raise recommended, player called
        if (recommended == ActionType.RAISE && actual == ActionType.CALL) {
            return List.of(
                "With " + eq + " equity you hold a range advantage — raising extracts more value.",
                "A call lets your opponent see the next card cheaply; a raise makes them pay or fold.",
                "Pot odds math: you only needed " + po + " to call, but " + eq + " equity warrants going for more.",
                "GTO strategy: raise with the top of your range, call with the middle.");
        }

        // Bet recommended, player checked
        if (recommended == ActionType.BET && actual == ActionType.CHECK) {
            return List.of(
                "With " + eq + " equity, betting charges opponents for their draws.",
                "Checking gives a free card — drawing hands improve without paying.",
                "GTO bets the top of its range; checking with strong equity is a missed value opportunity.",
                "Value bets also build the pot for future streets when you remain ahead.");
        }

        // Fold recommended, player called (or raised)
        if (recommended == ActionType.FOLD
                && (actual == ActionType.CALL || actual == ActionType.RAISE)) {
            double deficit = Math.max(0, potOdds - equity);
            return List.of(
                "Your " + eq + " equity means you'll lose this pot roughly " + pct(1 - equity) + " of the time.",
                "You need " + po + " equity to break even on a call — your " + eq + " falls " + pct(deficit) + " short.",
                "Continuing without the right price bleeds chips slowly across many hands.",
                "GTO folds when equity doesn't cover the cost of the call.");
        }

        // Call recommended, player folded
        if (recommended == ActionType.CALL && actual == ActionType.FOLD) {
            return List.of(
                "With " + eq + " equity you were mathematically priced in — pot odds were only " + po + ".",
                "Folding a profitable call means giving up expected value on this hand.",
                "GTO calls whenever equity exceeds pot odds, even with a modest edge.",
                "Over many hands, folding with equity leads to a significant chip loss.");
        }

        // Fold recommended, player raised (bluff raise without equity)
        if (recommended == ActionType.FOLD && actual == ActionType.RAISE) {
            return List.of(
                "Raising with only " + eq + " equity puts chips in as a significant underdog.",
                "Bluff raises require substantial fold equity to be profitable — and if called, " + eq + " is too weak.",
                "GTO raises as bluffs only with hands that have good equity or block strong opponent holdings.",
                "Folding preserves your stack for spots where you have a real equity advantage.");
        }

        // Generic fallback for any other combination
        return List.of(
            "Equity (" + eq + ") vs pot odds (" + po + ") is the foundation of every decision.",
            "GTO maximises expected value by betting/raising strong hands and folding weak ones.",
            "Consistent correct decisions compound into a winning edge over many hands.");
    }

    /** Formats a [0,1] probability as a percentage string, e.g. {@code "62%"}. */
    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
