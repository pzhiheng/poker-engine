package com.poker.domain.model;

/**
 * Coaching feedback generated immediately after a player's action.
 *
 * <p>Returned inside {@link com.poker.web.dto.ActionResponse} so the client
 * can display real-time coaching alongside the updated game state.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code actionTaken}      — what the player actually did</li>
 *   <li>{@code recommendedAction}— what GTO heuristics suggest was best</li>
 *   <li>{@code equity}           — player's estimated win probability (0–1)</li>
 *   <li>{@code potOdds}          — break-even call percentage (0 when no bet faces)</li>
 *   <li>{@code quality}          — OPTIMAL / ACCEPTABLE / SUBOPTIMAL / MISTAKE</li>
 *   <li>{@code explanation}      — human-readable coaching sentence</li>
 * </ul>
 */
public record ActionFeedback(
        ActionType      actionTaken,
        ActionType      recommendedAction,
        double          equity,
        double          potOdds,
        FeedbackQuality quality,
        String          explanation
) {}
