package com.poker.service;

import com.poker.domain.entity.Hand;
import com.poker.domain.entity.HandAction;
import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
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
 * Unit tests for {@link StatsComputationService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsComputationServiceTest {

    @Mock HandActionRepository actionRepo;
    @Mock HandRepository       handRepo;
    @Mock PlayerRepository     playerRepo;
    @Mock PotResultRepository  potRepo;

    StatsComputationService service;

    UUID playerId;
    Player player;
    Hand hand1;
    Hand hand2;

    @BeforeEach
    void setUp() throws Exception {
        service  = new StatsComputationService(actionRepo, handRepo, playerRepo, potRepo);
        playerId = UUID.randomUUID();
        player   = newPlayer(playerId, "alice", 1_000);
        hand1    = newHand(UUID.randomUUID());
        hand2    = newHand(UUID.randomUUID());

        when(playerRepo.findById(playerId)).thenReturn(Optional.of(player));
        when(potRepo.findByWinnerId(playerId)).thenReturn(List.of());
    }

    // ── No hands played ───────────────────────────────────────────────────────

    @Test
    void compute_noHandsPlayed_returnsZeroStats() {
        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(0L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of());

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.handsPlayed()).isEqualTo(0);
        assertThat(stats.vpip()).isEqualTo(0.0);
        assertThat(stats.pfr()).isEqualTo(0.0);
    }

    // ── VPIP ──────────────────────────────────────────────────────────────────

    @Test
    void compute_vpip_countsVoluntaryPreflopMoneyIn() throws Exception {
        // hand1: player calls preflop → VPIP
        // hand2: player folds preflop → no VPIP
        HandAction call1 = action(hand1, ActionType.CALL, 10, Street.PREFLOP, 1);
        HandAction fold2 = action(hand2, ActionType.FOLD, 0,  Street.PREFLOP, 1);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(2L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(call1, fold2));

        PlayerStats stats = service.compute(playerId);

        // 1 vpip hand / 2 hands = 0.5
        assertThat(stats.vpip()).isEqualTo(0.5);
    }

    // ── PFR ───────────────────────────────────────────────────────────────────

    @Test
    void compute_pfr_countsOnlyRaisesBetsPreflopPerHand() throws Exception {
        // hand1: player raises preflop → PFR
        // hand2: player calls preflop → no PFR
        HandAction raise1 = action(hand1, ActionType.RAISE, 20, Street.PREFLOP, 1);
        HandAction call2  = action(hand2, ActionType.CALL,  10, Street.PREFLOP, 1);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(2L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(raise1, call2));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.pfr()).isEqualTo(0.5);
        assertThat(stats.vpip()).isEqualTo(1.0); // both hands put money in
    }

    // ── Aggression factor ─────────────────────────────────────────────────────

    @Test
    void compute_aggressionFactor_betAndRaiseDividedByCallPostflop() throws Exception {
        // 2 bets/raises and 1 call postflop → AF = 2.0
        HandAction bet  = action(hand1, ActionType.BET,   30, Street.FLOP, 2);
        HandAction raise = action(hand1, ActionType.RAISE, 60, Street.TURN, 3);
        HandAction call  = action(hand2, ActionType.CALL,  30, Street.RIVER, 2);
        HandAction pfRaise = action(hand1, ActionType.RAISE, 20, Street.PREFLOP, 1); // ignored for AF

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(2L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(bet, raise, call, pfRaise));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.aggressionFactor()).isEqualTo(2.0);
    }

    @Test
    void compute_aggressionFactor_zeroCalls_returnsZero() throws Exception {
        // Only bets, no calls → return 0.0 (never passive)
        HandAction bet = action(hand1, ActionType.BET, 30, Street.FLOP, 2);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(1L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(bet));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.aggressionFactor()).isEqualTo(0.0);
    }

    // ── Avg profit ────────────────────────────────────────────────────────────

    @Test
    void compute_avgProfitPerHand_netChipsDividedByHandsPlayed() throws Exception {
        // Player invested 50 chips and won 150 chips over 2 hands → avg = (150-50)/2 = 50
        HandAction call = action(hand1, ActionType.CALL, 50, Street.PREFLOP, 1);

        Player winner = newPlayer(playerId, "alice", 1_000);
        PotResult pot = new PotResult(hand1, winner, 150, "last player standing");

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(2L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(call));
        when(potRepo.findByWinnerId(playerId)).thenReturn(List.of(pot));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.avgProfitPerHand()).isEqualTo(50.0);
    }

    // ── 3-bet% ────────────────────────────────────────────────────────────────

    @Test
    void compute_threeBetPct_playerRaisesAfterOwnPriorRaise_counted() throws Exception {
        // Simplified proxy: player has two preflop raises in the same hand at different
        // action orders — second raise "sees" a prior raise → qualifies as 3-bet hand.
        HandAction raise1 = action(hand1, ActionType.RAISE, 20, Street.PREFLOP, 1);
        HandAction raise2 = action(hand1, ActionType.RAISE, 60, Street.PREFLOP, 2);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(1L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(raise1, raise2));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.threeBetPct()).isEqualTo(1.0);
    }

    @Test
    void compute_threeBetPct_singlePreflopRaise_isZero() throws Exception {
        HandAction raise = action(hand1, ActionType.RAISE, 20, Street.PREFLOP, 1);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(1L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(raise));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.threeBetPct()).isEqualTo(0.0);
    }

    // ── WTSD% / W@SD% ────────────────────────────────────────────────────────

    @Test
    void compute_wtsdPct_sawFlopAndRiver_dividedBySawFlop() throws Exception {
        // hand1: flop + river actions  → goes to showdown
        // hand2: flop action only      → does not reach river
        HandAction flopAct1  = action(hand1, ActionType.BET,   30, Street.FLOP,  1);
        HandAction riverAct1 = action(hand1, ActionType.CHECK,  0, Street.RIVER, 2);
        HandAction flopAct2  = action(hand2, ActionType.CHECK,  0, Street.FLOP,  1);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(2L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(flopAct1, riverAct1, flopAct2));

        PlayerStats stats = service.compute(playerId);

        // sawFlop = 2, sawRiver = 1  →  wtsd = 0.5
        assertThat(stats.wtsdPct()).isEqualTo(0.5);
    }

    @Test
    void compute_wonAtSdPct_showdownWinsDividedByShowdownHands() throws Exception {
        // hand1: player reaches the river and wins
        HandAction flopAct  = action(hand1, ActionType.BET,   30, Street.FLOP,  1);
        HandAction riverAct = action(hand1, ActionType.CHECK,  0, Street.RIVER, 2);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(1L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(flopAct, riverAct));
        when(potRepo.countDistinctHandsWonByPlayerId(playerId)).thenReturn(1L);

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.wtsdPct()).isEqualTo(1.0);
        assertThat(stats.wonAtSdPct()).isEqualTo(1.0);
    }

    // ── VPIP deduplication ────────────────────────────────────────────────────

    @Test
    void compute_vpip_multipleCallsInSameHand_deduplicatedToOneHand() throws Exception {
        // Two CALL actions in the same hand — deduplicated to one VPIP hand
        HandAction call1 = action(hand1, ActionType.CALL, 30, Street.PREFLOP, 1);
        HandAction call2 = action(hand1, ActionType.CALL, 30, Street.PREFLOP, 2);

        when(handRepo.countHandsByPlayerAndStatus(playerId, HandStatus.FINISHED)).thenReturn(1L);
        when(actionRepo.findAllByPlayerId(playerId)).thenReturn(List.of(call1, call2));

        PlayerStats stats = service.compute(playerId);

        assertThat(stats.vpip()).isEqualTo(1.0); // 1 unique VPIP hand, not 2
    }

    // ── 404 on unknown player ────────────────────────────────────────────────

    @Test
    void compute_unknownPlayer_throws404() {
        UUID stranger = UUID.randomUUID();
        when(playerRepo.findById(stranger)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.compute(stranger));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HandAction action(Hand hand, ActionType type, int amount,
                              Street street, int order) {
        return new HandAction(hand, player, type, amount, order, street);
    }

    private static Player newPlayer(UUID id, String username, int bankroll) throws Exception {
        Player p = new Player(username, "hash", bankroll);
        var f = Player.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(p, id);
        return p;
    }

    private static Hand newHand(UUID id) throws Exception {
        PokerTable table = new PokerTable("T", 5, 10);
        Hand h = new Hand(table, 1);
        h.setStatus(HandStatus.FINISHED);
        var f = Hand.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(h, id);
        return h;
    }
}
