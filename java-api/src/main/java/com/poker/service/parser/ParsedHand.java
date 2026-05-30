package com.poker.service.parser;

import com.poker.domain.model.ActionType;

import java.time.Instant;
import java.util.List;

/**
 * Intermediate value object produced by a {@link HandHistoryParser} and
 * consumed by {@link com.poker.service.HandImportService}.
 *
 * <p>All fields reflect what is recorded in the original hand-history file —
 * no game-engine state is recomputed here.  The import service maps this
 * into JPA entities.
 *
 * <p>Note on {@code heroHoleCards}: PokerStars only shows the hole cards of
 * the player whose hand-history file it is.  The parser populates this from
 * the {@code "Dealt to <hero> [X Y]"} line; it is empty for observed hands.
 */
public record ParsedHand(
    String             handNumber,
    Instant            playedAt,
    int                smallBlind,
    int                bigBlind,
    String             tableName,
    int                buttonSeat,
    List<ParsedSeat>   seats,
    List<ParsedAction> actions,
    List<String>       heroHoleCards,
    List<String>       boardCards,
    List<ParsedWinner> winners
) {

    /** One seat in the hand. */
    public record ParsedSeat(
        int    seatNo,
        String username,
        int    stackChips
    ) {}

    /**
     * One recorded player action.
     *
     * <p>{@code street} is one of {@code "PREFLOP"}, {@code "FLOP"},
     * {@code "TURN"}, {@code "RIVER"}.
     *
     * <p>{@code amount} is the chips placed into the pot by this specific
     * action.  For RAISE actions this is the "raise-to" total (PokerStars
     * format: "raises X to Y" → stored amount = Y); for FOLD and CHECK
     * it is 0.
     */
    public record ParsedAction(
        String     username,
        String     street,
        ActionType actionType,
        int        amount
    ) {}

    /** A pot award recorded in the hand history (main pot or side pot). */
    public record ParsedWinner(
        String username,
        int    chipsWon
    ) {}
}
