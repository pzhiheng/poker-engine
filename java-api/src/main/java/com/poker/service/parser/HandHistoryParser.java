package com.poker.service.parser;

import java.util.List;

/**
 * Strategy interface for parsing a hand-history text file into a list of
 * {@link ParsedHand} value objects.
 *
 * <p>Each concrete implementation handles one site's proprietary format
 * (PokerStars, GGPoker, etc.).  Implementations must be stateless and
 * thread-safe — a single instance is reused across multiple import jobs.
 */
public interface HandHistoryParser {

    /**
     * Parses a raw hand-history text and returns one {@link ParsedHand} per
     * complete hand found in the input.
     *
     * <p>Malformed or unrecognisable hand blocks are silently skipped so that a
     * single bad record does not abort the entire import.
     *
     * @param rawText  full text of the hand-history file (UTF-8)
     * @return         ordered list of successfully parsed hands; never {@code null}
     */
    List<ParsedHand> parse(String rawText);
}
