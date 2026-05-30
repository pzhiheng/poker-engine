package com.poker.service.parser;

import com.poker.domain.model.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GGPokerParser}.
 * No Spring context — pure regex / logic tests.
 */
class GGPokerParserTest {

    GGPokerParser parser;

    // ── Sample hand strings ───────────────────────────────────────────────────

    /**
     * Minimal preflop-only hand: alice raises, bob folds, alice wins.
     * Note: table line has no max-seats descriptor; "Dealt to" has optional colon.
     */
    static final String PREFLOP_HAND = """
        Poker Hand #HD111111111: Hold'em No Limit (15/30) - 2024/03/01 10:00:00
        Table 'GGTestTable' Seat #1 is the button
        Seat 1: alice (1000 in chips)\s
        Seat 2: bob (980 in chips)\s
        alice: posts small blind 15
        bob: posts big blind 30
        *** HOLE CARDS ***
        Dealt to alice: [Ah Kd]
        alice: raises 60 to 90
        bob: folds
        alice: collected 60 from pot
        alice: doesn't show hand\s
        *** SUMMARY ***
        Total pot 60 | Rake 0\s
        Seat 1: alice (button) (small blind) collected (60)
        Seat 2: bob (big blind) folded before Flop
        """;

    /**
     * Hand with flop and turn action; alice check-raises the flop.
     */
    static final String FLOP_TURN_HAND = """
        Poker Hand #HD222222222: Hold'em No Limit (15/30) - 2024/03/01 10:05:00
        Table 'GGTestTable' Seat #2 is the button
        Seat 1: alice (955 in chips)\s
        Seat 2: bob (1030 in chips)\s
        Seat 3: charlie (1015 in chips)\s
        alice: posts small blind 15
        bob: posts big blind 30
        *** HOLE CARDS ***
        Dealt to alice: [8h 9h]
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
        alice: collected 615 from pot
        alice: doesn't show hand\s
        *** SUMMARY ***
        Total pot 615 | Rake 0\s
        Board [7c Th Jd 2s]
        Seat 1: alice (small blind) collected (615)
        Seat 2: bob (big blind) folded on the Turn
        Seat 3: charlie folded on the Flop
        """;

    /** Two GGPoker hands concatenated in one file. */
    static final String TWO_HANDS = PREFLOP_HAND + "\n" + FLOP_TURN_HAND;

    @BeforeEach
    void setUp() {
        parser = new GGPokerParser();
    }

    // ── Basic parsing ─────────────────────────────────────────────────────────

    @Test
    void parse_emptyInput_returnsEmptyList() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void parse_singleHand_returnsOneHand() {
        assertThat(parser.parse(PREFLOP_HAND)).hasSize(1);
    }

    @Test
    void parse_twoHandFile_returnsTwoHands() {
        assertThat(parser.parse(TWO_HANDS)).hasSize(2);
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
    void parse_tableName_extractedWithoutMaxSeatsDescriptor() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        // GGPoker table line: "Table 'GGTestTable' Seat #1 is the button"
        assertThat(hand.tableName()).isEqualTo("GGTestTable");
        assertThat(hand.buttonSeat()).isEqualTo(1);
    }

    // ── Dealt-to with colon ───────────────────────────────────────────────────

    @Test
    void parse_dealtToWithColon_heroHoleCardsExtracted() {
        // GGPoker uses "Dealt to alice: [Ah Kd]" (colon after username)
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.heroHoleCards()).containsExactly("Ah", "Kd");
    }

    // ── Collected with colon ──────────────────────────────────────────────────

    @Test
    void parse_collectedWithColon_winnerExtracted() {
        // GGPoker uses "alice: collected 60 from pot" (colon after username)
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);
        assertThat(hand.winners()).hasSize(1);
        assertThat(hand.winners().get(0).username()).isEqualTo("alice");
        assertThat(hand.winners().get(0).chipsWon()).isEqualTo(60);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Test
    void parse_preflopRaiseAndFold_parsedCorrectly() {
        ParsedHand hand = parser.parse(PREFLOP_HAND).get(0);

        List<ParsedHand.ParsedAction> aliceActions = hand.actions().stream()
            .filter(a -> a.username().equals("alice")).toList();
        List<ParsedHand.ParsedAction> bobActions = hand.actions().stream()
            .filter(a -> a.username().equals("bob")).toList();

        assertThat(aliceActions).hasSize(1);
        assertThat(aliceActions.get(0).actionType()).isEqualTo(ActionType.RAISE);
        assertThat(aliceActions.get(0).street()).isEqualTo("PREFLOP");

        assertThat(bobActions).hasSize(1);
        assertThat(bobActions.get(0).actionType()).isEqualTo(ActionType.FOLD);
    }

    @Test
    void parse_flopTurnActions_streetTaggedCorrectly() {
        ParsedHand hand = parser.parse(FLOP_TURN_HAND).get(0);

        List<ParsedHand.ParsedAction> aliceActions = hand.actions().stream()
            .filter(a -> a.username().equals("alice")).toList();

        // call preflop, check flop, raise flop, bet turn
        assertThat(aliceActions).hasSize(4);
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
        assertThat(parser.parse(PREFLOP_HAND).get(0).boardCards()).isEmpty();
    }

    @Test
    void parse_flopTurnHand_boardHasFourCards() {
        ParsedHand hand = parser.parse(FLOP_TURN_HAND).get(0);
        assertThat(hand.boardCards()).containsExactly("7c", "Th", "Jd", "2s");
    }

    // ── PokerStars file rejected ──────────────────────────────────────────────

    @Test
    void parse_pokerStarsFormat_returnsEmpty() {
        // GGPokerParser should not parse PokerStars hand blocks
        String psHand = """
            PokerStars Hand #999: Hold'em No Limit (15/30) - 2024/03/01 10:00:00 ET
            Table 'PSTable 6-max' 6-max Seat #1 is the button
            Seat 1: alice (1000 in chips)
            """;
        assertThat(parser.parse(psHand)).isEmpty();
    }
}
