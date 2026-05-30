package com.poker.service.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Parser for GGPoker hand-history text files (play-money and real-money).
 *
 * <h3>Recognised header format</h3>
 * <pre>
 * Poker Hand #HD123456789: Hold'em No Limit (15/30) - 2024/03/01 10:00:00
 * Table 'TableName' Seat #1 is the button
 * </pre>
 *
 * <p>Differences from PokerStars:
 * <ul>
 *   <li>Hand prefix is {@code "Poker Hand #HD"} (not {@code "PokerStars Hand #"})</li>
 *   <li>Table line has no max-seats descriptor between the name and {@code Seat #}</li>
 *   <li>Timestamp has no timezone suffix</li>
 *   <li>{@code "Dealt to"} and {@code "collected"} lines optionally include a colon
 *       after the username — the base-class patterns accept both variants</li>
 * </ul>
 *
 * <p>All shared parsing logic lives in {@link AbstractHandHistoryParser}.
 * This class only supplies the three site-specific patterns.
 */
@Component
public class GGPokerParser extends AbstractHandHistoryParser {

    private static final Pattern P_HEADER = Pattern.compile(
        "Poker Hand #HD(\\d+):\\s+.+?\\(\\$?([0-9.]+)/\\$?([0-9.]+).*?\\) - " +
        "(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})");

    /** GGPoker omits the max-seats descriptor; {@code Seat #} follows directly. */
    private static final Pattern P_TABLE = Pattern.compile(
        "Table '([^']+)' Seat #(\\d+) is the button");

    @Override
    protected String handBlockPrefix() { return "Poker Hand #HD"; }

    @Override
    protected Pattern headerPattern() { return P_HEADER; }

    @Override
    protected Pattern tablePattern()  { return P_TABLE; }
}
