package com.poker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.entity.Hand;
import com.poker.domain.entity.HandAction;
import com.poker.domain.entity.HandSnapshot;
import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.model.ActionFeedback;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.FeedbackQuality;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.SeatState;
import com.poker.domain.model.SnapshotPayload;
import com.poker.domain.model.TableStatus;
import com.poker.domain.repository.HandActionRepository;
import com.poker.domain.repository.HandRepository;
import com.poker.domain.repository.HandSnapshotRepository;
import com.poker.domain.repository.PlayerRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.web.dto.ActionRequest;
import com.poker.web.dto.ActionResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HandService#recordAction}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandServiceActionTest {

    @Mock PokerTableRepository    tableRepo;
    @Mock TableSeatRepository     seatRepo;
    @Mock HandRepository          handRepo;
    @Mock HandSnapshotRepository  snapshotRepo;
    @Mock HandActionRepository    actionRepo;
    @Mock PlayerRepository        playerRepo;
    @Mock DeckService             deckService;
    @Mock DecisionEvaluatorService evaluator;

    final ObjectMapper objectMapper = new ObjectMapper();

    HandService service;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    UUID handId;
    UUID playerAId;
    UUID playerBId;

    Hand      hand;
    Player    playerA;
    Player    playerB;
    PokerTable table;

    SeatState seatA;   // seat 1, acting
    SeatState seatB;   // seat 2, waiting

    SnapshotPayload preflopSnapshot;
    HandSnapshot    latestSnapshot;

    @BeforeEach
    void setUp() throws Exception {
        service = new HandService(tableRepo, seatRepo, handRepo, snapshotRepo,
            actionRepo, playerRepo, deckService, evaluator, objectMapper,
            new SimpleMeterRegistry());

        handId    = UUID.randomUUID();
        playerAId = UUID.randomUUID();
        playerBId = UUID.randomUUID();

        table   = newTable(UUID.randomUUID(), "T", 5, 10, TableStatus.IN_HAND);
        playerA = newPlayer(playerAId, "alice", 1_000);
        playerB = newPlayer(playerBId, "bob",   1_000);
        hand    = newHand(handId, table);

        // Heads-up, preflop: seat1 = SB/dealer, seat2 = BB
        // currentBet = 10 (BB), seat1 has contributed 5 (SB), seat2 has contributed 10 (BB)
        seatA = new SeatState(1, playerAId, "alice", 495, List.of("Ah", "Kh"),
            false, false, 5, false);   // needs to call 5 more (10-5)
        seatB = new SeatState(2, playerBId, "bob",   490, List.of("2c", "7d"),
            false, false, 10, false);  // already at currentBet

        preflopSnapshot = new SnapshotPayload(
            1, "PREFLOP", 15, 1, 1, 2,
            List.of(seatA, seatB), List.of(), 1,  // currentActionSeat = 1 (alice)
            10, 10,
            List.of("Qh", "Jd", "Ts", "9c", "8h"), 10
        );

        latestSnapshot = new HandSnapshot(hand, 1, objectMapper.writeValueAsString(preflopSnapshot));

        // Stubs used by most tests
        when(handRepo.findById(handId)).thenReturn(Optional.of(hand));
        when(snapshotRepo.findTopByHandIdOrderByVersionNoDesc(handId))
            .thenReturn(Optional.of(latestSnapshot));
        when(snapshotRepo.save(any(HandSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(actionRepo.save(any(HandAction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(handRepo.save(any(Hand.class))).thenAnswer(inv -> inv.getArgument(0));
        when(playerRepo.findById(playerAId)).thenReturn(Optional.of(playerA));
        when(evaluator.evaluate(any(), any(), any(), anyInt()))
            .thenReturn(new ActionFeedback(ActionType.CALL, ActionType.RAISE,
                0.62, 0.25, FeedbackQuality.SUBOPTIMAL, "Raising for value would be stronger."));
    }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    void recordAction_call_updatesPotAndAdvancesTurn() {
        ActionResponse resp = service.recordAction(handId, playerAId,
            new ActionRequest(ActionType.CALL, 5));

        // pot = 15 + 5 call = 20
        assertThat(resp.potChips()).isEqualTo(20);
        assertThat(resp.actionOrder()).isEqualTo(2);
        assertThat(resp.street()).isEqualTo("PREFLOP");
        assertThat(resp.feedback()).isNotNull();
        assertThat(resp.feedback().actionTaken()).isEqualTo(ActionType.CALL);
    }

    @Test
    void recordAction_fold_marksPlayerFoldedAndEndsHand() {
        // Only 2 players — fold ends the hand
        ActionResponse resp = service.recordAction(handId, playerAId,
            new ActionRequest(ActionType.FOLD, 0));

        assertThat(resp.nextSeat()).isEqualTo(-1);
        assertThat(hand.getStatus()).isEqualTo(HandStatus.FINISHED);
        // Seat A should be marked folded in the response
        assertThat(resp.seats().get(0).folded()).isTrue();
    }

    @Test
    void recordAction_raise_updatesBetAndOtherSeatMustAct() throws Exception {
        // Alice raises to 30 total (calls 5 more + raises 20 on top = 25 more)
        // amount=25 means she commits 25 chips this action
        ActionResponse resp = service.recordAction(handId, playerAId,
            new ActionRequest(ActionType.RAISE, 25));

        // pot = 15 + 25 = 40
        assertThat(resp.potChips()).isEqualTo(40);
        // Next to act should be seat 2 (Bob)
        assertThat(resp.nextSeat()).isEqualTo(2);
    }

    @Test
    void recordAction_check_whenNoBetFacing_isLegal() {
        // Recreate snapshot where currentBet == seatA.streetContribution (check is legal)
        SeatState evenA = new SeatState(1, playerAId, "alice", 490, List.of("Ah", "Kh"),
            false, false, 10, true); // already contributed 10
        SeatState evenB = new SeatState(2, playerBId, "bob",   490, List.of("2c", "7d"),
            false, false, 10, false); // bob hasn't acted yet

        SnapshotPayload postflopSnap = new SnapshotPayload(
            2, "FLOP", 20, 1, 1, 2,
            List.of(evenA, evenB), List.of("Qh", "Jd", "Ts"), 1,
            10, 10, List.of("9c", "8h"), 10
        );
        stubLatestSnapshot(postflopSnap);

        // pot = 0 currentBet for this street check if Alice can check
        // Actually the snapshot above has currentBet=10 but seatA contrib=10, so callAmount=0
        ActionResponse resp = service.recordAction(handId, playerAId,
            new ActionRequest(ActionType.CHECK, 0));

        // No chips added; pot stays at 20
        assertThat(resp.potChips()).isEqualTo(20);
        // Action recorded (version incremented)
        assertThat(resp.actionOrder()).isEqualTo(3); // version 2 snapshot + 1
    }

    @Test
    void recordAction_feedbackContainsEquityAndExplanation() {
        ActionResponse resp = service.recordAction(handId, playerAId,
            new ActionRequest(ActionType.CALL, 5));

        ActionFeedback fb = resp.feedback();
        assertThat(fb.equity()).isGreaterThan(0.0);
        assertThat(fb.potOdds()).isGreaterThan(0.0);
        assertThat(fb.explanation()).isNotBlank();
    }

    // ── Snapshot is persisted ─────────────────────────────────────────────────

    @Test
    void recordAction_persistsHandActionAndNewSnapshot() {
        service.recordAction(handId, playerAId, new ActionRequest(ActionType.CALL, 5));

        verify(actionRepo).save(any(HandAction.class));
        verify(snapshotRepo).save(any(HandSnapshot.class));
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test
    void recordAction_handNotFound_throws404() {
        when(handRepo.findById(handId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.recordAction(handId, playerAId,
                new ActionRequest(ActionType.CALL, 5)));
    }

    @Test
    void recordAction_handNotInProgress_throws422() {
        hand.setStatus(HandStatus.FINISHED);

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.recordAction(handId, playerAId,
                new ActionRequest(ActionType.CALL, 5)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("HAND_NOT_IN_PROGRESS");
    }

    @Test
    void recordAction_notYourTurn_throws422() {
        // Bob (seat 2) tries to act when it's Alice's (seat 1) turn
        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.recordAction(handId, playerBId,
                new ActionRequest(ActionType.CALL, 10)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("NOT_YOUR_TURN");
    }

    @Test
    void recordAction_checkWhenBetFacing_throws422() {
        // Alice has callAmount=5 so she cannot check
        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.recordAction(handId, playerAId,
                new ActionRequest(ActionType.CHECK, 0)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("CANNOT_CHECK");
    }

    @Test
    void recordAction_raiseTooSmall_throws422() {
        // Minimum raise = callAmount(5) + minRaise(10) = 15; sending 10 is too small
        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.recordAction(handId, playerAId,
                new ActionRequest(ActionType.RAISE, 10)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("RAISE_TOO_SMALL");
    }

    @Test
    void recordAction_notSeated_throws422() {
        UUID strangerID = UUID.randomUUID();

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.recordAction(handId, strangerID,
                new ActionRequest(ActionType.FOLD, 0)))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("NOT_SEATED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubLatestSnapshot(SnapshotPayload payload) {
        try {
            HandSnapshot snap = new HandSnapshot(hand, payload.version(),
                objectMapper.writeValueAsString(payload));
            when(snapshotRepo.findTopByHandIdOrderByVersionNoDesc(handId))
                .thenReturn(Optional.of(snap));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PokerTable newTable(UUID id, String name, int sb, int bb,
                                       TableStatus status) throws Exception {
        PokerTable t = new PokerTable(name, sb, bb);
        var f = PokerTable.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(t, id);
        t.setStatus(status);
        return t;
    }

    private static Player newPlayer(UUID id, String username, int bankroll) throws Exception {
        Player p = new Player(username, "hash", bankroll);
        var f = Player.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(p, id);
        return p;
    }

    private static Hand newHand(UUID id, PokerTable table) throws Exception {
        Hand h = new Hand(table, 1);
        h.setStatus(HandStatus.IN_PROGRESS);
        var f = Hand.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(h, id);
        return h;
    }
}
