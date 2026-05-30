package com.poker.service.parser;

import com.poker.domain.model.ActionType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses PokerStars hand-history text files (both play-money and real-money
 * formats) into {@link ParsedHand} records.
 *
 * <h3>Supported format</h3>
 * <pre>
 * PokerStars Hand #123:  Hold'em No Limit (15/30) - 2023/01/15 14:30:45 ET
 * Table 'TableName' 6-max Seat #1 is the button
 * Seat 1: alice (1000 in chips)
 * Seat 2: bob (980 in chips)
 * alice: posts small blind 15
 * bob: posts big blind 30
 * *** HOLE CARDS ***
 * Dealt to alice [Ah Kd]
 * alice: raises 60 to 90
 * bob: folds
 * alice collected 60 from pot
 * *** SUMMARY ***
 * Total pot 60 | Rake 0
 * Seat 1: alice (button) (small blind) collected (60)
 * Seat 2: bob (big blind) folded before Flop
 * </pre>
 *
 * <p>Multi-hand files are supported — each hand block starts with
 * {@code "PokerStars Hand #"} and is separated from the next by a blank line.
 *
 * <p><b>Known limitation:</b> for RAISE actions the stored {@code amount} is the
 * "raise-to" total (Y in "raises X to Y"), not just the increment X.  This
 * slightly over-counts total chips invested per hand but does not affect
 * VPIP/PFR/aggression stats which only inspect action type.
 */
@Component
public class PokerStarsParser implements HandHistoryParser {

    // ── Compile-time regex patterns ───────────────────────────────────────────

    /** Hand #, SB/BB (with or without $), timestamp. */
    private static final Pattern P_HEADER = Pattern.compile(
        "PokerStars Hand #(\\d+):\\s+.+?\\(\\$?([0-9.]+)/\\$?([0-9.]+).*?\\) - " +
        "(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})");

    /** Table name and button seat. */
    private static final Pattern P_TABLE = Pattern.compile(
        "Table '([^']+)' \\d+-max Seat #(\\d+) is the button");

    /** Seat number, username, stack — only in the pre-action seating block. */
    private static final Pattern P_SEAT = Pattern.compile(
        "Seat (\\d+): (\\S+) \\((\\d+) in chips\\)");

    /** Hero's two hole cards. */
    private static final Pattern P_DEALT = Pattern.compile(
        "Dealt to (\\S+) \\[([2-9TJQKAtjqka][cdhs]) ([2-9TJQKAtjqka][cdhs])\\]");

    /** Community cards revealed on each street. */
    private static final Pattern P_FLOP  = Pattern.compile(
        "\\*\\*\\* FLOP \\*\\*\\* \\[([^\\]]+)\\]");
    private static final Pattern P_TURN  = Pattern.compile(
        "\\*\\*\\* TURN \\*\\*\\* \\[[^\\]]+\\] \\[([^\\]]+)\\]");
    private static final Pattern P_RIVER = Pattern.compile(
        "\\*\\*\\* RIVER \\*\\*\\* \\[[^\\]]+\\] \\[([^\\]]+)\\]");

    /** Player actions — matched against stripped lines. */
    private static final Pattern P_FOLD       = Pattern.compile("^(\\S+): folds$");
    private static final Pattern P_CHECK      = Pattern.compile("^(\\S+): checks$");
    private static final Pattern P_CALL       = Pattern.compile("^(\\S+): calls (\\d+)( and is all-in)?$");
    private static final Pattern P_BET        = Pattern.compile("^(\\S+): bets (\\d+)( and is all-in)?$");
    private static final Pattern P_RAISE      = Pattern.compile("^(\\S+): raises \\d+ to (\\d+)( and is all-in)?$");

    /** "alice collected 60 from pot" — pre-summary win line. */
    private static final Pattern P_COLLECTED  = Pattern.compile(
        "^(\\S+) collected (\\d+) from (main |side )?pot$");

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    // ── HandHistoryParser ─────────────────────────────────────────────────────

    @Override
    public List<ParsedHand> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();

        // Normalise line endings, then split before each new hand header
        String normalised = rawText.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = normalised.split("\n\n(?=PokerStars Hand #)");

        List<ParsedHand> result = new ArrayList<>();
        for (String block : blocks) {
            if (!block.contains("PokerStars Hand #")) continue;
            try {
                ParsedHand hand = parseBlock(block.strip());
                if (hand != null) result.add(hand);
            } catch (Exception ignored) {
                // Malformed block — skip silently
            }
        }
        return List.copyOf(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ParsedHand parseBlock(String text) {
        String[] lines = text.split("\n");

        // ── Header (line 0) ───────────────────────────────────────────────────
        Matcher hm = P_HEADER.matcher(lines[0]);
        if (!hm.find()) return null;

        String  handNumber  = hm.group(1);
        int     smallBlind  = parseStake(hm.group(2));
        int     bigBlind    = parseStake(hm.group(3));
        Instant playedAt    = LocalDateTime.parse(hm.group(4), TS_FMT)
                                           .toInstant(ZoneOffset.UTC);

        // ── Table (scan first few lines) ─────────────────────────────────────
        String tableName  = "unknown";
        int    buttonSeat = 1;
        for (String line : lines) {
            Matcher tm = P_TABLE.matcher(line);
            if (tm.find()) {
                tableName  = tm.group(1);
                buttonSeat = Integer.parseInt(tm.group(2));
                break;
            }
        }

        // ── Seats, actions, board, winners — single pass ──────────────────────
        List<ParsedHand.ParsedSeat>   seats    = new ArrayList<>();
        List<ParsedHand.ParsedAction> actions  = new ArrayList<>();
        List<String>                  board    = new ArrayList<>();
        List<ParsedHand.ParsedWinner> winners  = new ArrayList<>();
        List<String>                  heroCrds = new ArrayList<>();

        String  currentStreet = "PREFLOP";
        boolean seatingDone   = false; // true once *** HOLE CARDS *** seen
        boolean inSummary     = false;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            // ── Section markers ───────────────────────────────────────────────
            if (line.equals("*** HOLE CARDS ***")) { seatingDone = true; continue; }
            if (line.startsWith("*** SUMMARY ***"))  { inSummary   = true; continue; }
            if (line.startsWith("*** SHOW DOWN ***")) continue;

            // ── Street advances ───────────────────────────────────────────────
            Matcher flop = P_FLOP.matcher(line);
            if (flop.find()) {
                currentStreet = "FLOP";
                for (String c : flop.group(1).split(" ")) if (!c.isBlank()) board.add(c);
                continue;
            }
            Matcher turn = P_TURN.matcher(line);
            if (turn.find()) {
                currentStreet = "TURN";
                board.add(turn.group(1).strip());
                continue;
            }
            Matcher river = P_RIVER.matcher(line);
            if (river.find()) {
                currentStreet = "RIVER";
                board.add(river.group(1).strip());
                continue;
            }

            if (!seatingDone) {
                // ── Seats block ───────────────────────────────────────────────
                Matcher sm = P_SEAT.matcher(line);
                if (sm.find()) {
                    seats.add(new ParsedHand.ParsedSeat(
                        Integer.parseInt(sm.group(1)),
                        sm.group(2),
                        Integer.parseInt(sm.group(3))));
                }
                continue;
            }

            if (inSummary) continue; // nothing useful to extract from summary

            // ── Dealt-to line (hero hole cards) ───────────────────────────────
            Matcher dm = P_DEALT.matcher(line);
            if (dm.find()) {
                heroCrds.add(dm.group(2));
                heroCrds.add(dm.group(3));
                continue;
            }

            // ── Win line (pre-summary) ────────────────────────────────────────
            Matcher wm = P_COLLECTED.matcher(line);
            if (wm.matches()) {
                winners.add(new ParsedHand.ParsedWinner(
                    wm.group(1), Integer.parseInt(wm.group(2))));
                continue;
            }

            // ── Action line ───────────────────────────────────────────────────
            ParsedHand.ParsedAction action = matchAction(line, currentStreet);
            if (action != null) actions.add(action);
        }

        return new ParsedHand(
            handNumber, playedAt, smallBlind, bigBlind,
            tableName, buttonSeat,
            List.copyOf(seats), List.copyOf(actions),
            List.copyOf(heroCrds), List.copyOf(board), List.copyOf(winners));
    }

    private ParsedHand.ParsedAction matchAction(String line, String street) {
        Matcher m;

        m = P_FOLD.matcher(line);
        if (m.matches())
            return new ParsedHand.ParsedAction(m.group(1), street, ActionType.FOLD, 0);

        m = P_CHECK.matcher(line);
        if (m.matches())
            return new ParsedHand.ParsedAction(m.group(1), street, ActionType.CHECK, 0);

        m = P_CALL.matcher(line);
        if (m.matches()) {
            boolean allIn = m.group(3) != null;
            return new ParsedHand.ParsedAction(m.group(1), street,
                allIn ? ActionType.ALL_IN : ActionType.CALL,
                Integer.parseInt(m.group(2)));
        }

        m = P_BET.matcher(line);
        if (m.matches()) {
            boolean allIn = m.group(3) != null;
            return new ParsedHand.ParsedAction(m.group(1), street,
                allIn ? ActionType.ALL_IN : ActionType.BET,
                Integer.parseInt(m.group(2)));
        }

        m = P_RAISE.matcher(line);
        if (m.matches()) {
            boolean allIn = m.group(3) != null;
            return new ParsedHand.ParsedAction(m.group(1), street,
                allIn ? ActionType.ALL_IN : ActionType.RAISE,
                Integer.parseInt(m.group(2))); // group(2) = raise-to total
        }

        return null;
    }

    /**
     * Converts a PokerStars stake string to an integer chip count.
     * Play-money: {@code "15"} → {@code 15}.
     * Real-money: {@code "0.25"} → {@code 25} cents.
     */
    static int parseStake(String s) {
        s = s.replace("$", "").strip();
        if (s.contains(".")) {
            return (int) Math.round(Double.parseDouble(s) * 100.0);
        }
        return Integer.parseInt(s);
    }
}
