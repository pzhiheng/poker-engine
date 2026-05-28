package com.poker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.entity.Hand;
import com.poker.domain.entity.HandSnapshot;
import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.entity.TableSeat;
import com.poker.domain.model.Card;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.TableStatus;
import com.poker.domain.repository.HandRepository;
import com.poker.domain.repository.HandSnapshotRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.web.dto.HandResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for {@link HandService} — all dependencies are mocked.
 *
 * <p>LENIENT strictness is used because the shared {@code @BeforeEach} stubs
 * (save, findByTableIdOrderByStartedAtDesc) are only exercised by the happy-path
 * tests; error-path tests fail before reaching those collaborators.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandServiceTest {

    @Mock PokerTableRepository   tableRepo;
    @Mock TableSeatRepository    seatRepo;
    @Mock HandRepository         handRepo;
    @Mock HandSnapshotRepository snapshotRepo;
    @Mock DeckService            deckService;

    // Real ObjectMapper for JSON serialisation in snapshots
    final ObjectMapper objectMapper = new ObjectMapper();

    HandService service;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    UUID tableId;
    UUID playerAId;
    UUID playerBId;
    UUID playerCId;

    PokerTable table;
    Player     playerA;
    Player     playerB;
    Player     playerC;

    @BeforeEach
    void setUp() throws Exception {
        service = new HandService(
            tableRepo, seatRepo, handRepo, snapshotRepo, deckService, objectMapper);

        tableId   = UUID.randomUUID();
        playerAId = UUID.randomUUID();
        playerBId = UUID.randomUUID();
        playerCId = UUID.randomUUID();

        table   = newTable(tableId, "Test Table", 5, 10, TableStatus.WAITING);
        playerA = newPlayer(playerAId, "alice", 1_000);
        playerB = newPlayer(playerBId, "bob",   1_000);
        playerC = newPlayer(playerCId, "carol", 1_000);

        // Stub handRepo.save to inject a UUID into the returned Hand
        when(handRepo.save(any(Hand.class))).thenAnswer(inv -> {
            Hand h = inv.getArgument(0);
            var f = Hand.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(h, UUID.randomUUID());
            return h;
        });
        when(snapshotRepo.save(any(HandSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tableRepo.save(any(PokerTable.class))).thenAnswer(inv -> inv.getArgument(0));

        // Default: no previously-saved hands for this table
        when(handRepo.findByTableIdOrderByStartedAtDesc(tableId)).thenReturn(List.of());
    }

    // ── Happy path — 2 players (heads-up) ────────────────────────────────────

    @Test
    void startHand_headsUp_postsBlindsDealCardsCreatesSnapshot() {
        TableSeat seatA = newSeat(table, playerA, 1, 500);
        TableSeat seatB = newSeat(table, playerB, 2, 500);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any())).thenReturn(Optional.empty());
        when(seatRepo.findByTableIdOrderBySeatNoAsc(tableId)).thenReturn(List.of(seatA, seatB));
        stubDeck();

        HandResponse resp = service.startHand(tableId, playerAId);

        // Pot = SB + BB = 5 + 10
        assertThat(resp.potChips()).isEqualTo(15);
        assertThat(resp.street()).isEqualTo("PREFLOP");

        // Heads-up: dealer = seat 1 (SB), seat 2 = BB
        assertThat(resp.dealerSeat()).isEqualTo(1);
        assertThat(resp.sbSeat()).isEqualTo(1);
        assertThat(resp.bbSeat()).isEqualTo(2);

        // Requesting player is in seat 1 → hole cards visible
        assertThat(resp.myHoleCards()).hasSize(2);
        assertThat(resp.myHoleCards()).doesNotContain("**");

        // Opponent seat 2 is masked
        HandResponse.SeatView opponentView = resp.seats().stream()
            .filter(s -> s.seatNo() == 2).findFirst().orElseThrow();
        assertThat(opponentView.holeCards()).containsExactly("**", "**");

        // Blinds deducted from stacks
        assertThat(seatA.getStackChips()).isEqualTo(495); // posted 5 (SB)
        assertThat(seatB.getStackChips()).isEqualTo(490); // posted 10 (BB)

        // Snapshot persisted
        verify(snapshotRepo).save(any(HandSnapshot.class));
        // Table advanced to IN_HAND
        assertThat(table.getStatus()).isEqualTo(TableStatus.IN_HAND);
    }

    // ── Happy path — 3 players ────────────────────────────────────────────────

    @Test
    void startHand_threePlayers_dealerSbBbRotateCorrectly() {
        TableSeat seatA = newSeat(table, playerA, 1, 500);
        TableSeat seatB = newSeat(table, playerB, 2, 500);
        TableSeat seatC = newSeat(table, playerC, 3, 500);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any())).thenReturn(Optional.empty());
        when(seatRepo.findByTableIdOrderBySeatNoAsc(tableId))
            .thenReturn(List.of(seatA, seatB, seatC));
        stubDeck();

        HandResponse resp = service.startHand(tableId, playerCId);

        // First hand: dealer = seat 1, SB = seat 2, BB = seat 3
        assertThat(resp.dealerSeat()).isEqualTo(1);
        assertThat(resp.sbSeat()).isEqualTo(2);
        assertThat(resp.bbSeat()).isEqualTo(3);

        // Carol (seat 3) is the BB — her hole cards are visible to herself
        assertThat(resp.myHoleCards()).hasSize(2).doesNotContain("**");
    }

    // ── Dealer rotation ───────────────────────────────────────────────────────

    @Test
    void startHand_rotateDealerFromPreviousHand() throws Exception {
        // Previous hand had dealer on seat 1 → new hand dealer should be seat 2
        Hand previousHand = newHand(table, 1);
        when(handRepo.findByTableIdOrderByStartedAtDesc(tableId))
            .thenReturn(List.of(previousHand));

        TableSeat seatA = newSeat(table, playerA, 1, 500);
        TableSeat seatB = newSeat(table, playerB, 2, 500);
        TableSeat seatC = newSeat(table, playerC, 3, 500);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any())).thenReturn(Optional.empty());
        when(seatRepo.findByTableIdOrderBySeatNoAsc(tableId))
            .thenReturn(List.of(seatA, seatB, seatC));
        stubDeck();

        HandResponse resp = service.startHand(tableId, playerAId);

        // Dealer should have moved from seat 1 → seat 2
        assertThat(resp.dealerSeat()).isEqualTo(2);
        assertThat(resp.sbSeat()).isEqualTo(3);   // SB = next after dealer
        assertThat(resp.bbSeat()).isEqualTo(1);   // BB = next after SB (wraps)
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test
    void startHand_tableNotFound_throws404() {
        when(tableRepo.findById(tableId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.startHand(tableId, playerAId))
            .withMessageContaining(tableId.toString());
    }

    @Test
    void startHand_tableNotWaiting_throws422() {
        table.setStatus(TableStatus.IN_HAND);
        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.startHand(tableId, playerAId))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("TABLE_NOT_WAITING");
    }

    @Test
    void startHand_handAlreadyActive_throws422() throws Exception {
        Hand active = newHand(table, 1);
        active.setStatus(HandStatus.IN_PROGRESS);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any()))
            .thenReturn(Optional.of(active));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.startHand(tableId, playerAId))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("HAND_ALREADY_ACTIVE");
    }

    @Test
    void startHand_insufficientPlayers_throws422() {
        TableSeat onlySeat = newSeat(table, playerA, 1, 500);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any())).thenReturn(Optional.empty());
        when(seatRepo.findByTableIdOrderBySeatNoAsc(tableId)).thenReturn(List.of(onlySeat));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> service.startHand(tableId, playerAId))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("INSUFFICIENT_PLAYERS");
    }

    // ── Spectator (not seated) sees no hole cards ────────────────────────────

    @Test
    void startHand_requestingPlayerNotSeated_myHoleCardsIsNull() {
        TableSeat seatA = newSeat(table, playerA, 1, 500);
        TableSeat seatB = newSeat(table, playerB, 2, 500);
        UUID spectatorId = UUID.randomUUID();

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any())).thenReturn(Optional.empty());
        when(seatRepo.findByTableIdOrderBySeatNoAsc(tableId)).thenReturn(List.of(seatA, seatB));
        stubDeck();

        HandResponse resp = service.startHand(tableId, spectatorId);

        assertThat(resp.myHoleCards()).isNull();
        // All seats masked for spectator
        resp.seats().forEach(s ->
            assertThat(s.holeCards()).containsExactly("**", "**"));
    }

    // ── Snapshot payload is valid JSON ────────────────────────────────────────

    @Test
    void startHand_snapshotPayloadIsValidJson() throws Exception {
        TableSeat seatA = newSeat(table, playerA, 1, 500);
        TableSeat seatB = newSeat(table, playerB, 2, 500);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(table));
        when(handRepo.findByTableIdAndStatusIn(eq(tableId), any())).thenReturn(Optional.empty());
        when(seatRepo.findByTableIdOrderBySeatNoAsc(tableId)).thenReturn(List.of(seatA, seatB));
        stubDeck();

        ArgumentCaptor<HandSnapshot> captor = ArgumentCaptor.forClass(HandSnapshot.class);
        service.startHand(tableId, playerAId);
        verify(snapshotRepo).save(captor.capture());

        HandSnapshot saved = captor.getValue();
        assertThat(saved.getVersionNo()).isEqualTo(1);
        // Must parse as JSON without error
        var node = objectMapper.readTree(saved.getPayload());
        assertThat(node.get("street").asText()).isEqualTo("PREFLOP");
        assertThat(node.get("pot").asInt()).isEqualTo(15);
        assertThat(node.get("board").isArray()).isTrue();
        assertThat(node.get("seats").isArray()).isTrue();
        assertThat(node.get("seats").size()).isEqualTo(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Provides a deterministic 52-card deck (Ac Kc Qc … 2s). */
    private void stubDeck() {
        when(deckService.shuffledDeck()).thenAnswer(inv -> new DeckService().shuffledDeck());
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

    private static TableSeat newSeat(PokerTable table, Player player, int seatNo, int stack) {
        return new TableSeat(table, player, seatNo, stack);
    }

    private static Hand newHand(PokerTable table, int dealerSeat) throws Exception {
        Hand h = new Hand(table, dealerSeat);
        var f = Hand.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(h, UUID.randomUUID());
        return h;
    }

    // Helper import for eq() matcher (duplicated here to keep import clean)
    private static <T> T eq(T t) {
        return org.mockito.ArgumentMatchers.eq(t);
    }
}
