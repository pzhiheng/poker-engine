package com.poker.service.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Parser for PokerStars hand-history text files (play-money and real-money).
 *
 * <h3>Recognised header format</h3>
 * <pre>
 * PokerStars Hand #123:  Hold'em No Limit (15/30) - 2024/03/01 10:00:00 ET
 * Table 'TableName 6-max' 6-max Seat #1 is the button
 * </pre>
 *
 * <p>All shared parsing logic lives in {@link AbstractHandHistoryParser}.
 * This class only supplies the three site-specific patterns.
 */
@Component
public class PokerStarsParser extends AbstractHandHistoryParser {

    private static final Pattern P_HEADER = Pattern.compile(
        "PokerStars Hand #(\\d+):\\s+.+?\\(\\$?([0-9.]+)/\\$?([0-9.]+).*?\\) - " +
        "(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})");

    /** PokerStars includes the max-seats descriptor before {@code Seat #}. */
    private static final Pattern P_TABLE = Pattern.compile(
        "Table '([^']+)' \\d+-max Seat #(\\d+) is the button");

    @Override
    protected String handBlockPrefix() { return "PokerStars Hand #"; }

    @Override
    protected Pattern headerPattern() { return P_HEADER; }

    @Override
    protected Pattern tablePattern()  { return P_TABLE; }
}
