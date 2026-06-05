package com.poker.service;

import com.poker.domain.event.HandActionEvent;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.SeatState;
import com.poker.domain.model.SnapshotPayload;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.web.dto.ActionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens for {@link HandActionEvent} and automatically plays on behalf of
 * the {@code bot-easy} player whenever it is the bot's turn.
 *
 * <p>Decision heuristic (equity-based):
 * <pre>
 * equity &gt; 0.65, facing bet → RAISE (2/3 pot)
 * equity &gt; 0.60, no bet     → BET   (2/3 pot)
 * equity 0.35-0.65, facing  → CALL
 * equity 0.35-0.60, no bet  → CHECK
 * equity &lt; 0.35, facing bet → FOLD
 * equity &lt; 0.35, no bet     → CHECK
 * </pre>
 *
 * <p>A random 400–900 ms delay is added to simulate human think time.
 */
@Service
public class BotActionService {

    private static final Logger log = LoggerFactory.getLogger(BotActionService.class);

    private static final double RAISE_THRESHOLD = 0.65;
    private static final double BET_THRESHOLD   = 0.60;
    private static final double FOLD_THRESHOLD  = 0.35;

    private final BotPlayerService     botPlayerService;
    private final TableSeatRepository  seatRepo;
    private final HandService          handService;
    private final EquityProvider       equityProvider;

    public BotActionService(BotPlayerService    botPlayerService,
                            TableSeatRepository seatRepo,
                            HandService         handService,
                            EquityProvider      equityProvider) {
        this.botPlayerService = botPlayerService;
        this.seatRepo         = seatRepo;
        this.handService      = handService;
        this.equityProvider   = equityProvider;
    }

    @Async
    @EventListener
    public void onHandAction(HandActionEvent event) {
        int nextSeat = event.nextActionSeat();
        if (nextSeat < 1) return; // hand over

        // Is the bot seated at this table?
        var botSeatOpt = seatRepo.findByTableIdAndPlayerId(
            event.tableId(), botPlayerService.getBotPlayerId());
        if (botSeatOpt.isEmpty()) return;

        int botSeatNo = botSeatOpt.get().getSeatNo();
        if (botSeatNo != nextSeat) return; // not the bot's turn

        // Find bot's seat state in the snapshot
        SnapshotPayload snap = event.snapshot();
        SeatState botSeat = snap.seats().stream()
            .filter(s -> s.playerId().equals(botPlayerService.getBotPlayerId()))
            .findFirst()
            .orElse(null);
        if (botSeat == null || botSeat.folded() || botSeat.allIn()) return;

        // Human-like delay
        try {
            Thread.sleep(400 + ThreadLocalRandom.current().nextInt(500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        ActionRequest action = decide(botSeat, snap);
        try {
            handService.recordAction(event.handId(), botPlayerService.getBotPlayerId(), action);
        } catch (Exception e) {
            log.warn("Bot action failed for hand {}: {}", event.handId(), e.getMessage());
        }
    }

    private ActionRequest decide(SeatState botSeat, SnapshotPayload snap) {
        long opponents = snap.seats().stream()
            .filter(s -> !s.folded() && !s.playerId().equals(botPlayerService.getBotPlayerId()))
            .count();
        double equity = equityProvider.calculateEquity(
            botSeat.holeCards(), snap.board(), (int) Math.max(1, opponents));

        int callAmount = Math.max(0, snap.currentBet() - botSeat.streetContribution());
        boolean facingBet = callAmount > 0;
        int pot = snap.pot();

        if (facingBet) {
            if (equity > RAISE_THRESHOLD) {
                int raiseTotal = snap.currentBet() + Math.max(snap.minRaise(), pot * 2 / 3);
                raiseTotal = Math.min(raiseTotal, botSeat.stackChips() + botSeat.streetContribution());
                return new ActionRequest(ActionType.RAISE, raiseTotal);
            }
            if (equity >= FOLD_THRESHOLD) {
                return new ActionRequest(ActionType.CALL, 0);
            }
            return new ActionRequest(ActionType.FOLD, 0);
        } else {
            if (equity > BET_THRESHOLD) {
                int betAmt = Math.max(snap.bigBlind() * 2, pot * 2 / 3);
                betAmt = Math.min(betAmt, botSeat.stackChips());
                return new ActionRequest(ActionType.BET, betAmt);
            }
            return new ActionRequest(ActionType.CHECK, 0);
        }
    }
}
