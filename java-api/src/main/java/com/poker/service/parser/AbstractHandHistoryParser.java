package com.poker.service.parser;

import com.poker.domain.model.ActionType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for poker site hand-history parsers.
 *
 * <p>Contains all parsing logic shared across sites — community-card patterns,
 * action patterns, the seat block, the dealt-to line, and the main parse loop.
 * Subclasses supply only the three patterns that differ site-to-site:
 *
 * <ul>
 *   <li>{@link #handBlockPrefix()}  — string that opens a new hand record</li>
 *   <li>{@link #headerPattern()}    — regex to extract hand#, stakes, timestamp</li>
 *   <li>{@link #tablePattern()}     — regex to extract table name + button seat</li>
 * </ul>
 *
 * <p>{@link #parse(String)} is {@code final} — override the three abstract
 * methods to customise behaviour, not the top-level entry point.
 */
public abstract class AbstractHandHistoryParser implements HandHistoryParser {

    // ── Abstract: site-specific ────────────────────────────────────────────────

    /** String that opens every hand block (used as a split anchor). */
    protected abstract String  handBlockPrefix();

    /**
     * Regex for the first line of a hand block.
     * Must capture: (1) hand number, (2) SB, (3) BB, (4) timestamp string.
     */
    protected abstract Pattern headerPattern();

    /**
     * Regex for the table/button line.
     * Must capture: (1) table name, (2) button seat number.
     */
    protected abstract Pattern tablePattern();

    // ── Common patterns (identical across all sites) ───────────────────────────

    /** {@code Seat N: username (stack in chips)} */
    private static final Pattern P_SEAT = Pattern.compile(
        "Seat (\\d+): (\\S+) \\((\\d+) in chips\\)");

    /**
     * Hole-cards line — optional colon after username for GGPoker compatibility:
     * {@code "Dealt to alice [Ah Kd]"} or {@code "Dealt to alice: [Ah Kd]"}.
     *
     * <p>{@code [^:\s]+} excludes the colon from the username capture group so that
     * GGPoker's {@code "Dealt to alice: [Ah Kd]"} yields {@code "alice"}, not
     * {@code "alice:"}.
     */
    private static final Pattern P_DEALT = Pattern.compile(
        "Dealt to ([^:\\s]+):? \\[([2-9TJQKAtjqka][cdhs]) ([2-9TJQKAtjqka][cdhs])\\]");

    private static final Pattern P_FLOP  = Pattern.compile(
        "\\*\\*\\* FLOP \\*\\*\\* \\[([^\\]]+)\\]");
    private static final Pattern P_TURN  = Pattern.compile(
        "\\*\\*\\* TURN \\*\\*\\* \\[[^\\]]+\\] \\[([^\\]]+)\\]");
    private static final Pattern P_RIVER = Pattern.compile(
        "\\*\\*\\* RIVER \\*\\*\\* \\[[^\\]]+\\] \\[([^\\]]+)\\]");

    private static final Pattern P_FOLD  = Pattern.compile("^(\\S+): folds$");
    private static final Pattern P_CHECK = Pattern.compile("^(\\S+): checks$");
    private static final Pattern P_CALL  = Pattern.compile("^(\\S+): calls (\\d+)( and is all-in)?$");
    private static final Pattern P_BET   = Pattern.compile("^(\\S+): bets (\\d+)( and is all-in)?$");
    private static final Pattern P_RAISE = Pattern.compile("^(\\S+): raises \\d+ to (\\d+)( and is all-in)?$");

    /**
     * Win line — optional colon after username:
     * {@code "alice collected 60 from pot"} or {@code "alice: collected 60 from pot"}.
     */
    private static final Pattern P_COLLECTED = Pattern.compile(
        "^([^:\\s]+):? collected (\\d+) from (main |side )?pot$");

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    // ── HandHistoryParser ─────────────────────────────────────────────────────

    /** Entry point — final so subclasses cannot bypass the shared split logic. */
    @Override
    public final List<ParsedHand> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();

        String prefix     = handBlockPrefix();
        String normalised = rawText.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks   = normalised.split("\n\n(?=" + Pattern.quote(prefix) + ")");

        List<ParsedHand> result = new ArrayList<>();
        for (String block : blocks) {
            if (!block.contains(prefix)) continue;
            try {
                ParsedHand hand = parseBlock(block.strip());
                if (hand != null) result.add(hand);
            } catch (Exception ignored) {
                // Malformed hand block — skip silently
            }
        }
        return List.copyOf(result);
    }

    // ── Shared parsing logic ──────────────────────────────────────────────────

    private ParsedHand parseBlock(String text) {
        String[] lines = text.split("\n");

        // ── Header (line 0) ───────────────────────────────────────────────────
        Matcher hm = headerPattern().matcher(lines[0]);
        if (!hm.find()) return null;

        String  handNumber = hm.group(1);
        int     smallBlind = parseStake(hm.group(2));
        int     bigBlind   = parseStake(hm.group(3));
        Instant playedAt   = LocalDateTime.parse(hm.group(4), TS_FMT)
                                          .toInstant(ZoneOffset.UTC);

        // ── Table ─────────────────────────────────────────────────────────────
        String tableName  = "unknown";
        int    buttonSeat = 1;
        for (String line : lines) {
            Matcher tm = tablePattern().matcher(line);
            if (tm.find()) {
                tableName  = tm.group(1);
                buttonSeat = Integer.parseInt(tm.group(2));
                break;
            }
        }

        // ── Single-pass: seats / actions / board / winners ────────────────────
        List<ParsedHand.ParsedSeat>   seats    = new ArrayList<>();
        List<ParsedHand.ParsedAction> actions  = new ArrayList<>();
        List<String>                  board    = new ArrayList<>();
        List<ParsedHand.ParsedWinner> winners  = new ArrayList<>();
        List<String>                  heroCrds = new ArrayList<>();

        String  currentStreet = "PREFLOP";
        boolean seatingDone   = false;
        boolean inSummary     = false;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            if (line.equals("*** HOLE CARDS ***"))    { seatingDone = true; continue; }
            if (line.startsWith("*** SUMMARY ***"))   { inSummary   = true; continue; }
            if (line.startsWith("*** SHOW DOWN ***"))              { continue; }

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
                Matcher sm = P_SEAT.matcher(line);
                if (sm.find()) {
                    seats.add(new ParsedHand.ParsedSeat(
                        Integer.parseInt(sm.group(1)),
                        sm.group(2),
                        Integer.parseInt(sm.group(3))));
                }
                continue;
            }

            if (inSummary) continue;

            Matcher dm = P_DEALT.matcher(line);
            if (dm.find()) {
                heroCrds.add(dm.group(2));
                heroCrds.add(dm.group(3));
                continue;
            }

            Matcher wm = P_COLLECTED.matcher(line);
            if (wm.matches()) {
                winners.add(new ParsedHand.ParsedWinner(
                    wm.group(1), Integer.parseInt(wm.group(2))));
                continue;
            }

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
            return new ParsedHand.ParsedAction(m.group(1), street,
                m.group(3) != null ? ActionType.ALL_IN : ActionType.CALL,
                Integer.parseInt(m.group(2)));
        }

        m = P_BET.matcher(line);
        if (m.matches()) {
            return new ParsedHand.ParsedAction(m.group(1), street,
                m.group(3) != null ? ActionType.ALL_IN : ActionType.BET,
                Integer.parseInt(m.group(2)));
        }

        m = P_RAISE.matcher(line);
        if (m.matches()) {
            return new ParsedHand.ParsedAction(m.group(1), street,
                m.group(3) != null ? ActionType.ALL_IN : ActionType.RAISE,
                Integer.parseInt(m.group(2)));
        }

        return null;
    }

    // ── Shared utility ─────────────────────────────────────────────────────────

    /**
     * Converts a stake string to an integer chip count.
     * Play-money: {@code "15"} → {@code 15}.
     * Real-money: {@code "0.25"} → {@code 25} (cents).
     */
    static int parseStake(String s) {
        s = s.replace("$", "").strip();
        if (s.contains(".")) {
            return (int) Math.round(Double.parseDouble(s) * 100.0);
        }
        return Integer.parseInt(s);
    }
}
