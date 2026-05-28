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
 */
public record SnapshotPayload(
        int          version,
        String       street,
        int          pot,
        int          dealerSeat,
        int          sbSeat,
        int          bbSeat,
        List<SeatState> seats,
        List<String> board,            // community cards ["Ah","Kd","2s", ...]
        int          currentActionSeat // whose turn it is (-1 if no action pending)
) {}
