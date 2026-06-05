package com.poker.domain.event;

import com.poker.domain.model.SnapshotPayload;

import java.util.UUID;

/**
 * Published after every hand action (start-hand and record-action) so that
 * the bot service can react without polling.
 *
 * <p>This is a plain Spring in-process event — no message broker required.
 * The snapshot carries the full pre-action state including hole cards and
 * board so the bot can compute equity and decide its next move.
 */
public record HandActionEvent(
        UUID            tableId,
        UUID            handId,
        int             nextActionSeat,  // -1 when hand is over
        SnapshotPayload snapshot
) {}
