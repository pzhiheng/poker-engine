package com.poker.service;

import com.poker.domain.model.ActionFeedback;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.FeedbackQuality;
import com.poker.domain.model.SeatState;
import com.poker.domain.model.SnapshotPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DecisionEvaluatorService}.
 *
 * <p>Uses a {@link StubEquityProvider} so tests are deterministic — the stub
 * always returns the same equity for a given hand.
 */
class DecisionEvaluatorServiceTest {

    private DecisionEvaluatorService evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new DecisionEvaluatorService(new StubEquityProvider());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SeatState seat(List<String> holeCards) {
        return new SeatState(1, UUID.randomUUID(), "alice", 500, holeCards,
            false, false, 0, false);
    }

    private SeatState opponent() {
        return new SeatState(2, UUID.randomUUID(), "bob", 500, List.of("2c", "7d"),
            false, false, 0, false);
    }

    private SnapshotPayload snapshot(int pot, int currentBet, List<String> board,
                                     SeatState acting, SeatState other) {
        return new SnapshotPayload(
            1, "FLOP", pot, 1, 1, 2,
            List.of(acting, other), board, acting.seatNo(),
            currentBet, 10,
            List.of("2s", "3h", "4d", "5c", "6h"), 10
        );
    }

    // ── OPTIMAL cases ─────────────────────────────────────────────────────────

    @Test
    void evaluate_strongHandCheckWhenNoBet_returnsOptimalBet() {
        // AA is strong → stub returns ~0.66 equity heads-up
        SeatState acting = seat(List.of("Ah", "Ad"));
        SnapshotPayload snap = snapshot(100, 0, List.of("Kh", "2d", "7c"), acting, opponent());

        ActionFeedback fb = evaluator.evaluate(acting, snap, ActionType.BET, 0);

        assertThat(fb.actionTaken()).isEqualTo(ActionType.BET);
        assertThat(fb.recommendedAction()).isEqualTo(ActionType.BET);
        assertThat(fb.quality()).isEqualTo(FeedbackQuality.OPTIMAL);
        assertThat(fb.equity()).isGreaterThan(0.60);
        assertThat(fb.potOdds()).isEqualTo(0.0);
        assertThat(fb.explanation()).isNotBlank();
    }

    @Test
    void evaluate_weakHandFoldVsHugeBet_returnsOptimalFold() {
        // 2-7 offsuit heads-up: stub gives ~0.40 equity.
        // With a very large bet (pot=10, bet=90), potOdds = 90/100 = 0.90 > 0.40 → FOLD recommended.
        SeatState acting = seat(List.of("2h", "7s"));
        SnapshotPayload snap = snapshot(10, 90, List.of("Ah", "Kd", "Qc"), acting, opponent());

        ActionFeedback fb = evaluator.evaluate(acting, snap, ActionType.FOLD, 90);

        assertThat(fb.recommendedAction()).isEqualTo(ActionType.FOLD);
        assertThat(fb.quality()).isEqualTo(FeedbackQuality.OPTIMAL);
    }

    // ── MISTAKE cases ─────────────────────────────────────────────────────────

    @Test
    void evaluate_foldWithStrongHand_returnsMistake() {
        // AA → equity ~0.66 > MISTAKE_FOLD_CUTOFF(0.50) → MISTAKE to fold
        SeatState acting = seat(List.of("Ah", "Ad"));
        SnapshotPayload snap = snapshot(100, 20, List.of("Kh", "2d", "7c"), acting, opponent());

        ActionFeedback fb = evaluator.evaluate(acting, snap, ActionType.FOLD, 20);

        assertThat(fb.quality()).isEqualTo(FeedbackQuality.MISTAKE);
        assertThat(fb.explanation()).contains("equity");
    }

    @Test
    void evaluate_callWithVeryWeakHandMultiway_returnsMistake() {
        // 2c-7d against 4 opponents: stub gives ~0.12 equity (below MISTAKE_CALL_CUTOFF of 0.20)
        SeatState acting = seat(List.of("2c", "7d"));
        // Add three extra opponents so numOpponents = 4
        SeatState opp2 = new SeatState(3, UUID.randomUUID(), "carol", 500,
            List.of("Kc", "Qc"), false, false, 0, false);
        SeatState opp3 = new SeatState(4, UUID.randomUUID(), "dave",  500,
            List.of("Jh", "Th"), false, false, 0, false);
        SeatState opp4 = new SeatState(5, UUID.randomUUID(), "eve",   500,
            List.of("9s", "8s"), false, false, 0, false);

        SnapshotPayload snap = new SnapshotPayload(
            1, "FLOP", 100, 1, 1, 2,
            List.of(acting, opponent(), opp2, opp3, opp4),
            List.of("Ah", "Kd", "Qc"), acting.seatNo(),
            30, 10, List.of("2s", "3h", "4d", "5c", "6h"), 10
        );

        ActionFeedback fb = evaluator.evaluate(acting, snap, ActionType.CALL, 30);

        // With 4 opponents, equity ≈ 0.40^2.2 ≈ 0.12 < MISTAKE_CALL_CUTOFF(0.20)
        assertThat(fb.equity()).isLessThan(0.20);
        assertThat(fb.quality()).isEqualTo(FeedbackQuality.MISTAKE);
    }

    // ── SUBOPTIMAL / ACCEPTABLE ───────────────────────────────────────────────

    @Test
    void evaluate_callWithStrongHandWhenRaiseIsBetter_returnsSuboptimalOrAcceptable() {
        // AA faces a bet; equity >> pot odds → RAISE recommended
        SeatState acting = seat(List.of("Ah", "Ad"));
        // call = 10, pot = 100, potOdds = 10/110 ≈ 0.09; equity >> 0.55 → RAISE recommended
        SnapshotPayload snap = snapshot(100, 10, List.of("Kh", "2d", "7c"), acting, opponent());

        ActionFeedback fb = evaluator.evaluate(acting, snap, ActionType.CALL, 10);

        assertThat(fb.recommendedAction()).isEqualTo(ActionType.RAISE);
        assertThat(fb.quality()).isIn(FeedbackQuality.SUBOPTIMAL, FeedbackQuality.ACCEPTABLE);
    }

    // ── Feedback fields are populated ────────────────────────────────────────

    @Test
    void evaluate_feedbackContainsAllFields() {
        SeatState acting = seat(List.of("Ah", "Kh"));
        SnapshotPayload snap = snapshot(80, 20, List.of("Qh", "Jh", "2c"), acting, opponent());

        ActionFeedback fb = evaluator.evaluate(acting, snap, ActionType.CALL, 20);

        assertThat(fb.actionTaken()).isEqualTo(ActionType.CALL);
        assertThat(fb.equity()).isStrictlyBetween(0.0, 1.0);
        assertThat(fb.potOdds()).isStrictlyBetween(0.0, 1.0);
        assertThat(fb.quality()).isNotNull();
        assertThat(fb.explanation()).isNotBlank();
    }
}
