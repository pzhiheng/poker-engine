package com.poker.domain.model;

import java.util.List;

/**
 * The complete, serialisable hand state stored in {@code hand_snapshots.payload}
 * as PostgreSQL {@code jsonb}.
 *
 * <p>One snapshot is written when a hand starts (version 1) and again after each
 * player action.  The snapshot always includes hole cards for all active seats so
 * that authorised replays can reconstruct the full board.
 *
 * <p>When returning this payload to clients, hole cards for opponents should be
 * masked to {@code ["**","**"]} — masking is done in the controller/service layer,
 * not in the stored snapshot.
 *
 * <p>Betting state fields:
 * <ul>
 *   <li>{@code currentBet}   — the highest total {@code streetContribution} any active
 *       player has made this street; the acting player must at least match it to stay in</li>
 *   <li>{@code minRaise}     — the minimum legal raise increment (initially the big blind;
 *       updated to the last raise size after each aggressive action)</li>
 * </ul>
 */
public record SnapshotPayload(
        int          version,
        String       street,
        int          pot,
        int          dealerSeat,
        int          sbSeat,
        int          bbSeat,
        List<SeatState> seats,
        List<String> board,             // revealed community cards (0/3/4/5)
        int          currentActionSeat, // whose turn it is (-1 if no action pending)
        int          currentBet,        // amount all active players must match to stay in
        int          minRaise,          // minimum legal raise increment
        List<String> pendingBoard,      // 5 pre-dealt cards, revealed street by street
        int          bigBlind           // stored so street resets can restore minRaise
) {}
