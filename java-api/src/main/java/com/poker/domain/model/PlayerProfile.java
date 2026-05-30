package com.poker.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Composite view of a player: their raw stats, their style classification,
 * and the coaching suggestions that follow from both.
 */
public record PlayerProfile(
    UUID                     playerId,
    String                   username,
    PlayerStats              stats,
    PlayerType               playerType,
    List<CoachingSuggestion> suggestions
) {}
