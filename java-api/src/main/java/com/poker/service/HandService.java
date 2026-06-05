package com.poker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.entity.Hand;
import com.poker.domain.entity.HandAction;
import com.poker.domain.entity.HandSnapshot;
import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.entity.TableSeat;
import com.poker.domain.model.ActionFeedback;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.Card;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.SeatState;
import com.poker.domain.model.SnapshotPayload;
import com.poker.domain.model.Street;
import com.poker.domain.model.TableStatus;
import com.poker.domain.repository.HandActionRepository;
import com.poker.domain.repository.HandRepository;
import com.poker.domain.repository.HandSnapshotRepository;
import com.poker.domain.repository.PlayerRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.domain.event.HandActionEvent;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.config.WebSocketConfig;
import com.poker.web.dto.ActionRequest;
import com.poker.web.dto.ActionResponse;
import com.poker.web.dto.HandResponse;
import com.poker.web.dto.TableEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application-layer service for hand lifecycle management.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@link #startHand} — deal hole cards, post blinds, create snapshot v1</li>
 *   <li>{@link #recordAction} — validate and apply a player action, advance state,
 *       return real-time coaching feedback</li>
 * </ul>
 */
@Service
@Transactional
public class HandService {

    private final PokerTableRepository    tableRepo;
    private final TableSeatRepository     seatRepo;
    private final HandRepository          handRepo;
    private final HandSnapshotRepository  snapshotRepo;
    private final HandActionRepository    actionRepo;
    private final PlayerRepository        playerRepo;
    private final DeckService             deckService;
    private final DecisionEvaluatorService evaluator;
    private final ObjectMapper            objectMapper;

    private final SimpMessagingTemplate   broker;
    private final ApplicationEventPublisher eventPublisher;

    /** Incremented once per {@link #startHand} call that succeeds. */
    private final Counter handsStartedCounter;
    /** Incremented when a hand ends because all others folded. */
    private final Counter handsFoldedCounter;
    /** Incremented when a hand reaches showdown. */
    private final Counter handsShowdownCounter;

    public HandService(PokerTableRepository     tableRepo,
                       TableSeatRepository      seatRepo,
                       HandRepository           handRepo,
                       HandSnapshotRepository   snapshotRepo,
                       HandActionRepository     actionRepo,
                       PlayerRepository         playerRepo,
                       DeckService              deckService,
                       DecisionEvaluatorService evaluator,
                       ObjectMapper             objectMapper,
                       MeterRegistry            meterRegistry,
                       SimpMessagingTemplate    broker,
                       ApplicationEventPublisher eventPublisher) {
        this.tableRepo      = tableRepo;
        this.seatRepo       = seatRepo;
        this.handRepo       = handRepo;
        this.snapshotRepo   = snapshotRepo;
        this.actionRepo     = actionRepo;
        this.playerRepo     = playerRepo;
        this.deckService    = deckService;
        this.evaluator      = evaluator;
        this.objectMapper   = objectMapper;
        this.broker         = broker;
        this.eventPublisher = eventPublisher;

        this.handsStartedCounter  = Counter.builder("poker.hands.started")
                .description("Total hands started")
                .register(meterRegistry);
        this.handsFoldedCounter   = Counter.builder("poker.hands.finished")
                .description("Hands finished when all others folded")
                .tag("reason", "folded")
                .register(meterRegistry);
        this.handsShowdownCounter = Counter.builder("poker.hands.finished")
                .description("Hands finished at showdown")
                .tag("reason", "showdown")
                .register(meterRegistry);
    }

    // ── Start hand ────────────────────────────────────────────────────────────

    /**
     * Starts a new hand: validates table state, rotates dealer, posts blinds,
     * deals hole cards, pre-deals all 5 community cards (stored but not yet revealed),
     * and creates {@link HandSnapshot} v1.
     */
    public HandResponse startHand(UUID tableId, UUID requestingPlayerId) {

        // 1. Load table
        PokerTable table = tableRepo.findById(tableId)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        // 2. Table must be WAITING
        if (table.getStatus() != TableStatus.WAITING) {
            throw new BusinessRuleException(
                "TABLE_NOT_WAITING",
                "Table '" + table.getName() + "' is " + table.getStatus()
                    + " — cannot start a new hand");
        }

        // 3. No active hand already in progress
        handRepo.findByTableIdAndStatusIn(
                tableId,
                List.of(HandStatus.WAITING, HandStatus.IN_PROGRESS, HandStatus.SHOWDOWN))
            .ifPresent(h -> {
                throw new BusinessRuleException(
                    "HAND_ALREADY_ACTIVE",
                    "A hand is already active at table '" + table.getName() + "'");
            });

        // 4. Collect active seats ordered by seatNo
        List<TableSeat> activeSeats = seatRepo.findByTableIdOrderBySeatNoAsc(tableId)
            .stream()
            .filter(TableSeat::isActive)
            .toList();

        if (activeSeats.size() < 2) {
            throw new BusinessRuleException(
                "INSUFFICIENT_PLAYERS",
                "Need at least 2 active players to start a hand; found " + activeSeats.size());
        }

        // 5. Rotate dealer button
        int dealerSeatNo = chooseDealerSeat(tableId, activeSeats);

        // 6. Determine SB / BB positions
        boolean headsUp  = activeSeats.size() == 2;
        int     sbSeatNo = headsUp ? dealerSeatNo : nextSeatNo(activeSeats, dealerSeatNo);
        int     bbSeatNo = nextSeatNo(activeSeats, sbSeatNo);

        // 7. Post blinds
        TableSeat sbSeat = findSeatBySeatNo(activeSeats, sbSeatNo);
        TableSeat bbSeat = findSeatBySeatNo(activeSeats, bbSeatNo);

        int sbPosted   = postBlind(sbSeat, table.getSmallBlind());
        int bbPosted   = postBlind(bbSeat, table.getBigBlind());
        int initialPot = sbPosted + bbPosted;

        // 8. Shuffle and deal: 2 hole cards per seat, then 5 board cards
        List<Card> deck = deckService.shuffledDeck();
        Map<Integer, List<String>> holeCards = dealHoleCards(activeSeats, deck);

        List<String> pendingBoard = List.of(
            deck.remove(0).toString(), deck.remove(0).toString(), deck.remove(0).toString(),
            deck.remove(0).toString(), deck.remove(0).toString()
        );

        // 9. Persist Hand
        Hand hand = new Hand(table, dealerSeatNo);
        hand.setStatus(HandStatus.IN_PROGRESS);
        hand.setPotChips(initialPot);
        hand = handRepo.save(hand);

        // 10. First to act preflop
        int actionSeatNo = headsUp ? sbSeatNo : nextSeatNo(activeSeats, bbSeatNo);

        // 11. Build initial snapshot
        List<SeatState> seatStates = buildInitialSeatStates(
            activeSeats, holeCards, sbSeatNo, sbPosted, bbSeatNo, bbPosted);

        SnapshotPayload payload = new SnapshotPayload(
            1, Street.PREFLOP.name(), initialPot,
            dealerSeatNo, sbSeatNo, bbSeatNo,
            seatStates, List.of(), actionSeatNo,
            bbPosted,              // currentBet = BB (everyone must match)
            table.getBigBlind(),   // minRaise = 1 big blind
            pendingBoard,
            table.getBigBlind()
        );

        snapshotRepo.save(new HandSnapshot(hand, 1, serialise(payload)));

        // 12. Advance table status
        table.setStatus(TableStatus.IN_HAND);
        tableRepo.save(table);

        handsStartedCounter.increment();

        // Broadcast the initial table state to all connected STOMP clients.
        broker.convertAndSend(
            WebSocketConfig.TABLE_TOPIC + tableId,
            TableEvent.from(tableId, hand.getId(), Street.PREFLOP.name(),
                            initialPot, actionSeatNo, seatStates));

        // Notify the bot service so it can act if it is seated.
        eventPublisher.publishEvent(
            new HandActionEvent(tableId, hand.getId(), actionSeatNo, payload));

        return buildHandResponse(hand, table, seatStates, dealerSeatNo, sbSeatNo, bbSeatNo,
                                 payload.board(), requestingPlayerId);
    }

    // ── Record action ─────────────────────────────────────────────────────────

    /**
     * Validates and applies a player action, advances the hand state, and returns
     * coaching feedback.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>Hand must exist and be {@code IN_PROGRESS}</li>
     *   <li>Requesting player must be seated (not folded)</li>
     *   <li>It must be the requesting player's turn</li>
     *   <li>Action must be legal for the current betting situation</li>
     * </ul>
     */
    public ActionResponse recordAction(UUID handId, UUID requestingPlayerId, ActionRequest req) {

        // 1. Load hand
        Hand hand = handRepo.findById(handId)
            .orElseThrow(() -> new ResourceNotFoundException("Hand not found: " + handId));

        if (hand.getStatus() != HandStatus.IN_PROGRESS) {
            throw new BusinessRuleException("HAND_NOT_IN_PROGRESS",
                "Hand " + handId + " is " + hand.getStatus() + " — cannot record action");
        }

        // 2. Load latest snapshot
        HandSnapshot latestSnapshot = snapshotRepo.findTopByHandIdOrderByVersionNoDesc(handId)
            .orElseThrow(() -> new IllegalStateException("No snapshot found for hand " + handId));
        SnapshotPayload before = deserialise(latestSnapshot.getPayload());

        // 3. Find acting seat
        SeatState actingSeat = before.seats().stream()
            .filter(s -> s.playerId().equals(requestingPlayerId))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException("NOT_SEATED",
                "You are not seated at this table"));

        if (actingSeat.folded()) {
            throw new BusinessRuleException("ALREADY_FOLDED", "You have already folded this hand");
        }

        // 4. Validate turn
        if (before.currentActionSeat() != actingSeat.seatNo()) {
            throw new BusinessRuleException("NOT_YOUR_TURN",
                "It is seat " + before.currentActionSeat() + "'s turn, not yours (seat "
                    + actingSeat.seatNo() + ")");
        }

        // 5. Compute call amount and validate action legality
        int callAmount = Math.max(0, before.currentBet() - actingSeat.streetContribution());
        validateAction(req.actionType(), req.amount(), actingSeat, callAmount, before.minRaise());

        // 6. Apply action: compute chips committed, update seats
        int chipsCommitted = chipsForAction(req.actionType(), req.amount(), actingSeat, callAmount);

        List<SeatState> updatedSeats = applyActionToSeat(
            before.seats(), actingSeat.seatNo(), req.actionType(), chipsCommitted);

        // Update currentBet / minRaise if aggressive
        int newCurrentBet = before.currentBet();
        int newMinRaise   = before.minRaise();
        if (req.actionType().isAggressive()) {
            int totalContrib   = actingSeat.streetContribution() + chipsCommitted;
            int raiseIncrement = totalContrib - before.currentBet();
            newCurrentBet = totalContrib;
            newMinRaise   = Math.max(before.minRaise(), raiseIncrement);
            // All other active seats must act again
            updatedSeats = resetHasActedExcept(updatedSeats, actingSeat.seatNo());
        }

        int newPot = before.pot() + chipsCommitted;

        // 7. Determine next state
        long activeCount = updatedSeats.stream().filter(s -> !s.folded()).count();
        boolean handOver    = activeCount <= 1;
        boolean roundDone   = !handOver && isRoundComplete(updatedSeats, newCurrentBet);

        String       newStreet      = before.street();
        List<String> newBoard       = before.board();
        int          nextActionSeat = -1;
        int          resetBet       = newCurrentBet;
        int          resetMinRaise  = newMinRaise;
        List<String> newPending     = before.pendingBoard();

        if (handOver) {
            hand.setStatus(HandStatus.FINISHED);
            // hand.getTable() is already loaded (same transaction); no extra DB round-trip needed
            hand.getTable().setStatus(TableStatus.WAITING);
            tableRepo.save(hand.getTable());
            handsFoldedCounter.increment();

        } else if (roundDone) {
            Street current = Street.valueOf(before.street());
            if (current == Street.RIVER || current == Street.SHOWDOWN) {
                hand.setStatus(HandStatus.SHOWDOWN);
                newStreet = Street.SHOWDOWN.name();
                handsShowdownCounter.increment();
            } else {
                Street next = current.next();
                newStreet       = next.name();
                newBoard        = dealBoardCards(before.board(), before.pendingBoard(), next);
                updatedSeats    = resetStreetContributions(updatedSeats);
                resetBet        = 0;
                resetMinRaise   = before.bigBlind();
                nextActionSeat  = firstToActPostflop(updatedSeats, before.dealerSeat());
                hand.setStreet(next);
            }
        } else {
            nextActionSeat = nextActiveSeat(updatedSeats, actingSeat.seatNo());
        }

        // 8. Persist HandAction (with street for stats computation)
        int nextVersion = latestSnapshot.getVersionNo() + 1;
        Street actionStreet = Street.valueOf(before.street());
        Player player = playerRepo.findById(requestingPlayerId)
            .orElseThrow(() -> new ResourceNotFoundException("Player not found: " + requestingPlayerId));
        actionRepo.save(new HandAction(hand, player, req.actionType(), chipsCommitted, nextVersion, actionStreet));

        // 9. Persist new snapshot
        SnapshotPayload after = new SnapshotPayload(
            nextVersion, newStreet, newPot,
            before.dealerSeat(), before.sbSeat(), before.bbSeat(),
            updatedSeats, newBoard, nextActionSeat,
            resetBet, resetMinRaise, newPending, before.bigBlind()
        );
        snapshotRepo.save(new HandSnapshot(hand, nextVersion, serialise(after)));

        // 10. Update hand aggregate
        hand.setPotChips(newPot);
        handRepo.save(hand);

        // 11. Compute coaching feedback (uses pre-action snapshot)
        ActionFeedback feedback = evaluator.evaluate(actingSeat, before, req.actionType(), callAmount);

        // Broadcast the updated table state so all STOMP subscribers see the change.
        UUID tableId = hand.getTable().getId();
        broker.convertAndSend(
            WebSocketConfig.TABLE_TOPIC + tableId,
            TableEvent.from(tableId, handId, newStreet, newPot, nextActionSeat, updatedSeats));

        // Notify the bot service so it can act if it is the next player.
        eventPublisher.publishEvent(
            new HandActionEvent(tableId, handId, nextActionSeat, after));

        return new ActionResponse(
            handId, nextVersion, newStreet, newPot, nextActionSeat,
            after.board(),
            buildSeatViews(updatedSeats, requestingPlayerId),
            feedback
        );
    }

    // ── Action helpers ────────────────────────────────────────────────────────

    private void validateAction(ActionType type, int amount, SeatState seat,
                                int callAmount, int minRaise) {
        switch (type) {
            case CHECK -> {
                if (callAmount > 0) throw new BusinessRuleException("CANNOT_CHECK",
                    "There is a bet of " + callAmount + " chips to call — cannot check");
            }
            case BET -> {
                if (callAmount > 0) throw new BusinessRuleException("INVALID_ACTION",
                    "There is already a bet — use RAISE, CALL, or FOLD");
                if (amount < minRaise) throw new BusinessRuleException("BET_TOO_SMALL",
                    "Minimum bet is " + minRaise + " chips");
                if (amount > seat.stackChips()) throw new BusinessRuleException("INSUFFICIENT_STACK",
                    "Bet of " + amount + " exceeds your stack of " + seat.stackChips());
            }
            case RAISE -> {
                if (callAmount == 0) throw new BusinessRuleException("INVALID_ACTION",
                    "No bet to raise — use BET, CHECK, or FOLD");
                int minTotal = callAmount + minRaise;
                if (amount < minTotal) throw new BusinessRuleException("RAISE_TOO_SMALL",
                    "Minimum raise is " + minTotal + " chips total (call " + callAmount
                        + " + raise " + minRaise + ")");
                if (amount > seat.stackChips()) throw new BusinessRuleException("INSUFFICIENT_STACK",
                    "Raise of " + amount + " exceeds your stack of " + seat.stackChips());
            }
            default -> {} // FOLD, CALL, ALL_IN always legal
        }
    }

    /** Returns the chips the player actually commits for this action. */
    private int chipsForAction(ActionType type, int reqAmount, SeatState seat, int callAmount) {
        return switch (type) {
            case FOLD, CHECK -> 0;
            case CALL        -> Math.min(callAmount, seat.stackChips()); // all-in if short
            case BET, RAISE  -> Math.min(reqAmount, seat.stackChips());
            case ALL_IN      -> seat.stackChips();
        };
    }

    /** Returns updated seat list with the acting seat's state changed. */
    private List<SeatState> applyActionToSeat(List<SeatState> seats, int actingSeatNo,
                                               ActionType type, int chips) {
        return seats.stream().map(s -> {
            if (s.seatNo() != actingSeatNo) return s;
            boolean nowFolded = (type == ActionType.FOLD);
            int     newStack  = s.stackChips() - chips;
            boolean nowAllIn  = !nowFolded && newStack == 0;
            return new SeatState(
                s.seatNo(), s.playerId(), s.username(), newStack,
                s.holeCards(), nowFolded, nowAllIn || s.allIn(),
                s.streetContribution() + chips,
                true  // this seat has now voluntarily acted
            );
        }).toList();
    }

    /**
     * After an aggressive action (BET/RAISE/ALL_IN that increases the bet),
     * all other active seats need to act again.
     */
    private List<SeatState> resetHasActedExcept(List<SeatState> seats, int exceptSeatNo) {
        return seats.stream().map(s -> {
            if (s.seatNo() == exceptSeatNo || s.folded()) return s;
            return new SeatState(s.seatNo(), s.playerId(), s.username(),
                s.stackChips(), s.holeCards(), s.folded(), s.allIn(),
                s.streetContribution(), false);
        }).toList();
    }

    /**
     * A betting round is complete when every non-folded, non-all-in seat has
     * both acted voluntarily AND matched the current bet.
     */
    private boolean isRoundComplete(List<SeatState> seats, int currentBet) {
        return seats.stream()
            .filter(s -> !s.folded() && !s.allIn())
            .allMatch(s -> s.hasActedThisStreet() && s.streetContribution() == currentBet);
    }

    /** Next non-folded, non-all-in seat after {@code afterSeatNo} (wraps). */
    private int nextActiveSeat(List<SeatState> seats, int afterSeatNo) {
        List<SeatState> candidates = seats.stream()
            .filter(s -> !s.folded() && !s.allIn())
            .toList();
        if (candidates.isEmpty()) return -1;
        for (SeatState s : candidates) {
            if (s.seatNo() > afterSeatNo) return s.seatNo();
        }
        return candidates.get(0).seatNo();
    }

    /** First active seat left of the dealer (used for postflop streets). */
    private int firstToActPostflop(List<SeatState> seats, int dealerSeat) {
        List<SeatState> candidates = seats.stream()
            .filter(s -> !s.folded() && !s.allIn())
            .toList();
        if (candidates.isEmpty()) return -1;
        for (SeatState s : candidates) {
            if (s.seatNo() > dealerSeat) return s.seatNo();
        }
        return candidates.get(0).seatNo();
    }

    /** Reveals the right number of community cards for the advancing street. */
    private List<String> dealBoardCards(List<String> currentBoard,
                                         List<String> pending, Street next) {
        return switch (next) {
            case FLOP -> List.of(pending.get(0), pending.get(1), pending.get(2));
            case TURN -> {
                List<String> updated = new ArrayList<>(currentBoard);
                updated.add(pending.get(3));
                yield List.copyOf(updated);
            }
            case RIVER -> {
                List<String> updated = new ArrayList<>(currentBoard);
                updated.add(pending.get(4));
                yield List.copyOf(updated);
            }
            default -> currentBoard;
        };
    }

    /** Resets per-street betting state for all seats at the start of a new street. */
    private List<SeatState> resetStreetContributions(List<SeatState> seats) {
        return seats.stream().map(s -> new SeatState(
            s.seatNo(), s.playerId(), s.username(), s.stackChips(),
            s.holeCards(), s.folded(), s.allIn(), 0, false
        )).toList();
    }

    private List<HandResponse.SeatView> buildSeatViews(List<SeatState> seats,
                                                         UUID requestingPlayerId) {
        return seats.stream().map(s -> new HandResponse.SeatView(
            s.seatNo(), s.playerId(), s.username(), s.stackChips(),
            s.playerId().equals(requestingPlayerId) ? s.holeCards() : List.of("**", "**"),
            s.folded(), s.allIn()
        )).toList();
    }

    // ── Start-hand helpers ────────────────────────────────────────────────────

    private int chooseDealerSeat(UUID tableId, List<TableSeat> activeSeats) {
        List<Hand> recent = handRepo.findByTableIdOrderByStartedAtDesc(tableId);
        if (recent.isEmpty()) return activeSeats.get(0).getSeatNo();
        return nextSeatNo(activeSeats, recent.get(0).getDealerSeat());
    }

    private int nextSeatNo(List<TableSeat> activeSeats, int currentSeatNo) {
        for (TableSeat seat : activeSeats) {
            if (seat.getSeatNo() > currentSeatNo) return seat.getSeatNo();
        }
        return activeSeats.get(0).getSeatNo();
    }

    private TableSeat findSeatBySeatNo(List<TableSeat> activeSeats, int seatNo) {
        return activeSeats.stream()
            .filter(s -> s.getSeatNo() == seatNo)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Active seat " + seatNo + " not found — data inconsistency"));
    }

    private int postBlind(TableSeat seat, int amount) {
        int posted = Math.min(amount, seat.getStackChips());
        seat.setStackChips(seat.getStackChips() - posted);
        return posted;
    }

    private Map<Integer, List<String>> dealHoleCards(List<TableSeat> activeSeats,
                                                      List<Card> deck) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (TableSeat seat : activeSeats) result.put(seat.getSeatNo(), new ArrayList<>());
        for (int round = 0; round < 2; round++) {
            for (TableSeat seat : activeSeats) {
                result.get(seat.getSeatNo()).add(deck.remove(0).toString());
            }
        }
        return result;
    }

    private List<SeatState> buildInitialSeatStates(List<TableSeat> activeSeats,
                                                    Map<Integer, List<String>> holeCards,
                                                    int sbSeatNo, int sbPosted,
                                                    int bbSeatNo, int bbPosted) {
        return activeSeats.stream().map(seat -> {
            int contrib = 0;
            if (seat.getSeatNo() == sbSeatNo) contrib = sbPosted;
            else if (seat.getSeatNo() == bbSeatNo) contrib = bbPosted;
            return new SeatState(
                seat.getSeatNo(), seat.getPlayer().getId(), seat.getPlayer().getUsername(),
                seat.getStackChips(), holeCards.get(seat.getSeatNo()),
                false, false, contrib,
                false  // blinds do not count as voluntary actions
            );
        }).toList();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private String serialise(SnapshotPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise snapshot payload", e);
        }
    }

    private SnapshotPayload deserialise(String json) {
        try {
            return objectMapper.readValue(json, SnapshotPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise snapshot payload", e);
        }
    }

    // ── Response builders ─────────────────────────────────────────────────────

    private HandResponse buildHandResponse(Hand hand, PokerTable table,
                                            List<SeatState> seatStates,
                                            int dealerSeat, int sbSeat, int bbSeat,
                                            List<String> boardCards,
                                            UUID requestingPlayerId) {
        List<String> myHoleCards = null;
        List<HandResponse.SeatView> seats = new ArrayList<>();

        for (SeatState state : seatStates) {
            boolean isMe = state.playerId().equals(requestingPlayerId);
            if (isMe) myHoleCards = state.holeCards();
            seats.add(new HandResponse.SeatView(
                state.seatNo(), state.playerId(), state.username(),
                state.stackChips(),
                isMe ? state.holeCards() : List.of("**", "**"),
                state.folded(), state.allIn()
            ));
        }

        return new HandResponse(hand.getId(), table.getId(), hand.getStreet().name(),
            hand.getPotChips(), dealerSeat, sbSeat, bbSeat, boardCards, myHoleCards, seats);
    }
}
