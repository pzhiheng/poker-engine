package com.poker.web.dto;

import com.poker.domain.model.CoachingSuggestion;
import com.poker.domain.model.PlayerProfile;
import com.poker.domain.model.PlayerStats;

import java.util.List;
import java.util.UUID;

/**
 * HTTP response payload for {@code GET /players/{id}/profile}.
 *
 * <p>{@code playerType} is the enum name (e.g. {@code "TAG"});
 * {@code playerTypeDescription} is the human-readable label.
 * All percentage stats are fractions in [0.0, 1.0].
 */
public record PlayerProfileResponse(
    UUID                     playerId,
    String                   username,
    PlayerStats              stats,
    String                   playerType,
    String                   playerTypeDescription,
    List<SuggestionView>     suggestions
) {

    /**
     * Flat view of a {@link CoachingSuggestion} suitable for JSON serialisation.
     */
    public record SuggestionView(
        String category,
        String severity,
        String title,
        String detail
    ) {
        static SuggestionView from(CoachingSuggestion s) {
            return new SuggestionView(
                s.category(),
                s.severity().name(),
                s.title(),
                s.detail()
            );
        }
    }

    /** Factory: builds from a domain {@link PlayerProfile}. */
    public static PlayerProfileResponse from(PlayerProfile profile) {
        return new PlayerProfileResponse(
            profile.playerId(),
            profile.username(),
            profile.stats(),
            profile.playerType().name(),
            profile.playerType().getDescription(),
            profile.suggestions().stream()
                .map(SuggestionView::from)
                .toList()
        );
    }
}
