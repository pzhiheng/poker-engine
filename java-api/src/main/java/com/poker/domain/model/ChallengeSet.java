package com.poker.domain.model;

import java.util.List;

/**
 * A themed collection of challenges presented as a sequential quiz session.
 */
public record ChallengeSet(
        String       id,            // URL-safe slug
        String       title,
        String       description,
        String       icon,          // emoji
        String       difficulty,    // "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "MIXED"
        List<String> challengeIds   // ordered list — determines quiz sequence
) {}
