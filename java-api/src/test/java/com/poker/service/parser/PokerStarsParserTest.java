package com.poker.service.parser;

import com.poker.domain.model.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PokerStarsParser}.
 * No Spring context — pure regex / logic tests.
 */
class PokerStarsParserTest {

    PokerStarsParser parser;

    // ── Sample hand strings ───────────────────────────────────────────────────

    /**
     * Minimal preflop-only hand: alice raises, bob folds, alice wins.
     */
    static final String PREFLOP_HAND = """
        PokerStars Hand #111111111:  Hold'em No Limit (15/30) - 2024/03/01 10:00:00 ET
        Table 'TestTable 6-max' 6-max Seat #1 is the button
        Seat 1: alice (1000 in chips)\s
        Seat 2: bob (980 in chips)\s
        alice: posts small blind 15
        bob: posts big blind 30
        *** HOLE CARDS ***
        Dealt to alice [Ah Kd]
        alice: raises 60 to 90
        bob: folds
        Uncalled bet (60) returned to alice
        alice collected 60 from pot
        alice: doesn't show hand\s
        *** SUMMARY ***
        Total pot 60 | Rake 0\s
        Seat 1: alice (button) (small blind) collected (60)
        Seat 2: bob (big blind) folded before Flop
        """;

    /**
     * Hand going to the turn with a flop check-raise.
     */
    static final String FLOP_TURN_HAND = """
        PokerStars Hand #222222222:  Hold'em No Limit (15/30) - 2024/03/01 10:05:00 ET
        Table 'TestTable 6-max' 6-max Seat #2 is the button
        Seat 1: alice (955 in chips)\s
        Seat 2: bob (1030 in chips)\s
        Seat 3: charlie (1015 in chips)\s
        alice: posts small blind 15
        bob: posts big blind 30
        *** HOLE CARDS ***
        Dealt to alice [8h 9h]
        charlie: calls 30
        alice: calls 15
        bob: checks
        *** FLOP *** [7c Th Jd]
        alice: checks
        bob: bets 45
        charlie: folds
        alice: raises 135 to 180
        bob: calls 135
        *** TURN *** [7c Th Jd] [2s]
        alice: bets 225
        bob: folds
        Uncalled bet (225) returned to alice
        alice collected 615 from pot
        alice: doesn't show hand\s
        *** SUMMARY ***
        Total pot 615 | Rake 0\s
        Board [7c Th Jd 2s]
        Seat 1: alice (small blind) collected (615)
        Seat 2: bob (big blind) folded on the Turn
        Seat 3: charlie folded on the Flop
        """;

    /**
     * Two hands concatenated (multi-hand file).
     */
    static final String TWO_HANDS = PREFLOP_HAND + "\n" + FLOP_TURN_HAND;

    /**
     * Hand where both players go all-in pre-flop.
     * Tests {@code ActionType.ALL_IN} detection via the "and is all-in" suffix.
     */
    static final String ALLIN_HAND = """
        PokerStars Hand #333333333:  Hold'em No Limit (15/30) - 2024/03/01 10:10:00 ET
        Table 'TestTable 6-max' 6-max Seat #1 is the button
        Seat 1: alice (100 in chips)\s
        Seat 2: bob (200 in chips)\s
        alice: posts small blind 15
        bob: posts big blind 30
        *** HOLE CARDS ***
        Dealt to alice [Ah Kd]
        alice: raises 70 to 100 and is all-in
        bob: calls 70 and is all-in
        alice collected 200 from pot
        *** SUMMARY ***
        Total pot 200 | Rake 0\s
        Seat 1: alice (button) (small blind) collected (200)
        Seat 2: bob (big blind) lost
        """;

    /**
     * Real-money hand ($0.25/$0.50) — stakes must be converted to cents (25/50).
     */
    static final String REAL_MONEY_HAND = """
        PokerStars Hand #444444444:  Hold'em No Limit ($0.25/$0.50) - 2024/03/01 10:15:00 ET
        Table 'RealMoneyTable 6-max' 6-max Seat #1 is the button
        Seat 1: alice (50000 in chips)\s
        Seat 2: bob (49000 in chips)\s
        alice: posts small blind 25
        bob: posts big blind 50
        *** HOLE CARDS ***
        Dealt to alice [Ah Kd]
        alice: raises 125 to 175
        bob: folds
        Uncalled bet (125) returned to alice
        alice collected 100 from pot
        *** SUMMARY ***
        Total pot 100 | Rake 0\s
        Seat 1: alice (button) (small blind) collected (100)
        Seat 2: bob (big blind) folded before Flop
        """;

    /**
     * Full river hand — board must contain all five community cards.
     */
    static final String RIVER_HAND = """
        PokerStars Hand #555555555:  Hold'em No Limit (15/30) - 2024/03/01 10:20:00 ET
        Table 'TestTable 6-max' 6-max Seat #1 is the button
        Seat 1: alice (1000 in chips)\s
        Seat 2: bob (1000 in chips)\s
        alice: posts small blind 15
        bob: posts big blind 30
        *** HOLE CARDS ***
        Dealt to alice [8h 9h]
        alice: calls 15
        bob: checks
        *** FLOP *** [7c Th Jd]
        alice: bets 45
        bob: calls 45
        *** TURN *** [7c Th Jd] [2s]
        alice: checks
        bob: checks
        *** RIVER *** [7c Th Jd 2s] [Ac]
        alice: bets 90
        bob: folds
        Uncalled bet (90) returned to alice
        alice collected 180 from pot
        *** SUMMARY ***
        Total pot 180 | Rake 0\s
        Board [7c Th Jd 2s Ac]
        Seat 1: alice (small blind) collected (180)
        Seat 2: bob (big blind) folded on the River
        """;

    @BeforeEach
    void setUp() {
        parser = new PokerStarsParser();
    }

    // ── Basic parsing ─────────────────────────────────────────────────────────

    @Test
    void parse_emptyInput_returnsEmptyList() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void parse_singleHand_returnsOneHand() {
        List<ParsedHand> hands = parser.parse(PREFLOP_HAND);
        assertThat(hands).hasSize(1);
    }

    @Test
    void parse_twoHandFile_returnsTwoHands() {
        List<ParsedHand> hands = parser.parse(TWO_HANDS);
        assertThat(hands).hasSize(2);
    }

    // ── Header fields ─────────────────────────────────────────────────────────

    @Test
    void parse_handNumber_extracted() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.handNumber()).isEqualTo("111111111");
    }

    @Test
    void parse_stakes_extracted() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.smallBlind()).isEqualTo(15);
        assertThat(hand.bigBlind()).isEqualTo(30);
    }

    @Test
    void parse_tableName_extracted() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.tableName()).isEqualTo("TestTable 6-max");
    }

    @Test
    void parse_buttonSeat_extracted() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.buttonSeat()).isEqualTo(1);
    }

    // ── Seats ─────────────────────────────────────────────────────────────────

    @Test
    void parse_seats_extractedCorrectly() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.seats()).hasSize(2);
        assertThat(hand.seats().get(0).username()).isEqualTo("alice");
        assertThat(hand.seats().get(0).stackChips()).isEqualTo(1000);
        assertThat(hand.seats().get(1).username()).isEqualTo("bob");
    }

    // ── Hole cards ────────────────────────────────────────────────────────────

    @Test
    void parse_heroHoleCards_extracted() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.heroHoleCards()).containsExactly("Ah", "Kd");
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Test
    void parse_preflopActions_raiseAndFold() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);

        List<ParsedHand.ParsedAction> aliceActions = hand.actions().stream()
            .filter(a -> a.username().equals("alice")).toList();
        List<ParsedHand.ParsedAction> bobActions = hand.actions().stream()
            .filter(a -> a.username().equals("bob")).toList();

        assertThat(aliceActions).hasSize(1);
        assertThat(aliceActions.get(0).actionType()).isEqualTo(ActionType.RAISE);
        assertThat(aliceActions.get(0).amount()).isEqualTo(90); // raise-to amount
        assertThat(aliceActions.get(0).street()).isEqualTo("PREFLOP");

        assertThat(bobActions).hasSize(1);
        assertThat(bobActions.get(0).actionType()).isEqualTo(ActionType.FOLD);
    }

    @Test
    void parse_postflopActions_streetTaggedCorrectly() {
        ParsedHand hand = parser.parse(FLOP_TURN_HAND).get(0);

        // alice: checks (FLOP), raises (FLOP), bets (TURN)
        List<ParsedHand.ParsedAction> aliceActions = hand.actions().stream()
            .filter(a -> a.username().equals("alice")).toList();

        assertThat(aliceActions).hasSize(4); // call preflop, check flop, raise flop, bet turn
        assertThat(aliceActions.get(0).street()).isEqualTo("PREFLOP");
        assertThat(aliceActions.get(0).actionType()).isEqualTo(ActionType.CALL);
        assertThat(aliceActions.get(1).street()).isEqualTo("FLOP");
        assertThat(aliceActions.get(1).actionType()).isEqualTo(ActionType.CHECK);
        assertThat(aliceActions.get(2).street()).isEqualTo("FLOP");
        assertThat(aliceActions.get(2).actionType()).isEqualTo(ActionType.RAISE);
        assertThat(aliceActions.get(3).street()).isEqualTo("TURN");
        assertThat(aliceActions.get(3).actionType()).isEqualTo(ActionType.BET);
    }

    // ── Board cards ───────────────────────────────────────────────────────────

    @Test
    void parse_preflopOnly_boardIsEmpty() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.boardCards()).isEmpty();
    }

    @Test
    void parse_flopTurnHand_boardHasFourCards() {
        ParsedHand hand = parser.parse(FLOP_TURN_HAND).get(0);
        assertThat(hand.boardCards()).containsExactly("7c", "Th", "Jd", "2s");
    }

    // ── Winners ───────────────────────────────────────────────────────────────

    @Test
    void parse_winner_extractedFromCollectedLine() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.winners()).hasSize(1);
        assertThat(hand.winners().get(0).username()).isEqualTo("alice");
        assertThat(hand.winners().get(0).chipsWon()).isEqualTo(60);
    }

    // ── parseStake helper (shared utility, lives in AbstractHandHistoryParser) ──

    @Test
    void parseStake_playMoney_returnsInteger() {
        assertThat(AbstractHandHistoryParser.parseStake("15")).isEqualTo(15);
        assertThat(AbstractHandHistoryParser.parseStake("30")).isEqualTo(30);
    }

    @Test
    void parseStake_realMoney_convertsToCents() {
        assertThat(AbstractHandHistoryParser.parseStake("0.01")).isEqualTo(1);
        assertThat(AbstractHandHistoryParser.parseStake("0.50")).isEqualTo(50);
        assertThat(AbstractHandHistoryParser.parseStake("$0.25")).isEqualTo(25);
    }

    // ── All-in actions ────────────────────────────────────────────────────────

    @Test
    void parse_allInRaise_parsedAsAllIn() {
        // "alice: raises 70 to 100 and is all-in" → ALL_IN (not RAISE)
        ParsedHand hand = parser.parse(ALLIN_HAND).get(0);

        List<ParsedHand.ParsedAction> aliceActions = hand.actions().stream()
            .filter(a -> a.username().equals("alice")).toList();

        assertThat(aliceActions).hasSize(1);
        assertThat(aliceActions.get(0).actionType()).isEqualTo(ActionType.ALL_IN);
        assertThat(aliceActions.get(0).amount()).isEqualTo(100); // raise-to amount
    }

    @Test
    void parse_allInCall_parsedAsAllIn() {
        // "bob: calls 70 and is all-in" → ALL_IN (not CALL)
        ParsedHand hand = parser.parse(ALLIN_HAND).get(0);

        List<ParsedHand.ParsedAction> bobActions = hand.actions().stream()
            .filter(a -> a.username().equals("bob")).toList();

        assertThat(bobActions).hasSize(1);
        assertThat(bobActions.get(0).actionType()).isEqualTo(ActionType.ALL_IN);
    }

    // ── Real-money stakes ─────────────────────────────────────────────────────

    @Test
    void parse_realMoneyHeader_stakesConvertedToCents() {
        // $0.25/$0.50 → sb=25 cents, bb=50 cents
        ParsedHand hand = parser.parse(REAL_MONEY_HAND).get(0);
        assertThat(hand.smallBlind()).isEqualTo(25);
        assertThat(hand.bigBlind()).isEqualTo(50);
    }

    // ── River board ───────────────────────────────────────────────────────────

    @Test
    void parse_riverHand_boardHasFiveCards() {
        ParsedHand hand = parser.parse(RIVER_HAND).get(0);
        assertThat(hand.boardCards()).containsExactly("7c", "Th", "Jd", "2s", "Ac");
    }

    @Test
    void parse_riverHand_actionsTaggedCorrectly() {
        ParsedHand hand = parser.parse(RIVER_HAND).get(0);

        List<ParsedHand.ParsedAction> aliceActions = hand.actions().stream()
            .filter(a -> a.username().equals("alice")).toList();

        // call preflop, bet flop, check turn, bet river
        assertThat(aliceActions).hasSize(4);
        assertThat(aliceActions.get(2).street()).isEqualTo("TURN");
        assertThat(aliceActions.get(2).actionType()).isEqualTo(ActionType.CHECK);
        assertThat(aliceActions.get(3).street()).isEqualTo("RIVER");
        assertThat(aliceActions.get(3).actionType()).isEqualTo(ActionType.BET);
    }
}
