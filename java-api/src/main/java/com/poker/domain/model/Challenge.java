package com.poker.domain.model;

import java.util.List;

/**
 * A training scenario that presents the player with a specific poker situation
 * and asks them to choose the correct GTO action.
 */
public record Challenge(
        String       id,             // URL-safe slug
        String       title,
        String       description,    // situation narrative shown to the player
        String       position,       // "UTG", "MP", "CO", "BTN", "SB", "BB"
        List<String> holeCards,      // ["Ah","7d"]
        List<String> board,          // [] for preflop, 3-5 cards otherwise
        String       street,         // "PREFLOP","FLOP","TURN","RIVER"
        int          potChips,
        int          callAmount,     // 0 if no bet facing
        int          effectiveStack,
        List<String> options,        // exactly 4 answer choices
        int          correctIndex,   // 0-based
        String       explanation,    // full GTO reasoning revealed after answering
        List<String> keyPoints,      // 2-4 bullet concepts
        String       difficulty      // "BEGINNER","INTERMEDIATE","ADVANCED"
) {}
