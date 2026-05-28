package com.poker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.entity.Hand;
import com.poker.domain.entity.HandSnapshot;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.entity.TableSeat;
import com.poker.domain.model.Card;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.SeatState;
import com.poker.domain.model.SnapshotPayload;
import com.poker.domain.model.Street;
import com.poker.domain.model.TableStatus;
import com.poker.domain.repository.HandRepository;
import com.poker.domain.repository.HandSnapshotRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.web.dto.HandResponse;
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
 * <p>Currently exposes only {@link #startHand}, which deals hole cards, posts
 * blinds, and creates the initial {@code HandSnapshot}.  Future days will add
 * action recording and hand completion logic.
 */
@Service
@Transactional
public class HandService {

    private final PokerTableRepository    tableRepo;
    private final TableSeatRepository     seatRepo;
    private final HandRepository          handRepo;
    private final HandSnapshotRepository  snapshotRepo;
    private final DeckService             deckService;
    private final ObjectMapper            objectMapper;

    public HandService(PokerTableRepository   tableRepo,
                       TableSeatRepository    seatRepo,
                       HandRepository         handRepo,
                       HandSnapshotRepository snapshotRepo,
                       DeckService            deckService,
                       ObjectMapper           objectMapper) {
        this.tableRepo    = tableRepo;
        this.seatRepo     = seatRepo;
        this.handRepo     = handRepo;
        this.snapshotRepo = snapshotRepo;
        this.deckService  = deckService;
        this.objectMapper = objectMapper;
    }

    // ── Start hand ────────────────────────────────────────────────────────────

    /**
     * Starts a new hand at the given table.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>Table must exist</li>
     *   <li>Table must be {@code WAITING} (not already {@code IN_HAND} or {@code CLOSED})</li>
     *   <li>No active hand already exists for the table</li>
     *   <li>2–6 active (occupied, not sitting-out) seats required</li>
     * </ul>
     *
     * <p>Procedure:
     * <ol>
     *   <li>Rotate dealer button from last completed hand (or pick first seat)</li>
     *   <li>Post small blind and big blind, deducting from seat stacks</li>
     *   <li>Deal 2 hole cards per active seat using a shuffled deck</li>
     *   <li>Persist {@link Hand} + {@link HandSnapshot} (version 1)</li>
     *   <li>Advance table status to {@code IN_HAND}</li>
     * </ol>
     *
     * @param tableId           UUID of the target table
     * @param requestingPlayerId UUID of the authenticated player (for hole-card visibility)
     * @return {@link HandResponse} with cards visible for the requesting player,
     *         opponents' cards masked as {@code ["**","**"]}
     * @throws ResourceNotFoundException if the table does not exist
     * @throws BusinessRuleException     if any business rule is violated
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

        // 4. Collect active seats (occupied + not sitting-out), ordered by seatNo
        List<TableSeat> activeSeats = seatRepo.findByTableIdOrderBySeatNoAsc(tableId)
            .stream()
            .filter(TableSeat::isActive)
            .toList();

        if (activeSeats.size() < 2) {
            throw new BusinessRuleException(
                "INSUFFICIENT_PLAYERS",
                "Need at least 2 active players to start a hand; found "
                    + activeSeats.size());
        }

        // 5. Rotate dealer button
        int dealerSeatNo = chooseDealerSeat(tableId, activeSeats);

        // 6. Determine SB / BB positions
        boolean headsUp  = activeSeats.size() == 2;
        int     sbSeatNo = headsUp ? dealerSeatNo          : nextSeatNo(activeSeats, dealerSeatNo);
        int     bbSeatNo = nextSeatNo(activeSeats, sbSeatNo);

        // 7. Post blinds (deduct from stacks; short-stack goes all-in)
        TableSeat sbSeat = findSeatBySeatNo(activeSeats, sbSeatNo);
        TableSeat bbSeat = findSeatBySeatNo(activeSeats, bbSeatNo);

        int sbPosted = postBlind(sbSeat, table.getSmallBlind());
        int bbPosted = postBlind(bbSeat, table.getBigBlind());
        int initialPot = sbPosted + bbPosted;

        // 8. Deal 2 hole cards per active seat
        List<Card>                deck      = deckService.shuffledDeck();
        Map<Integer, List<String>> holeCards = dealHoleCards(activeSeats, deck);

        // 9. Persist the Hand entity
        Hand hand = new Hand(table, dealerSeatNo);
        hand.setStatus(HandStatus.IN_PROGRESS);
        hand.setPotChips(initialPot);
        hand = handRepo.save(hand);

        // 10. First player to act preflop is left of BB (heads-up: SB/dealer acts first)
        int actionSeatNo = headsUp ? sbSeatNo : nextSeatNo(activeSeats, bbSeatNo);

        // 11. Build and persist the initial snapshot
        List<SeatState> seatStates = buildSeatStates(activeSeats, holeCards);

        SnapshotPayload payload = new SnapshotPayload(
            1,
            Street.PREFLOP.name(),
            initialPot,
            dealerSeatNo,
            sbSeatNo,
            bbSeatNo,
            seatStates,
            List.of(),        // no board cards yet at preflop
            actionSeatNo
        );

        String payloadJson = serialise(payload);
        snapshotRepo.save(new HandSnapshot(hand, 1, payloadJson));

        // 12. Advance table status
        table.setStatus(TableStatus.IN_HAND);
        tableRepo.save(table);

        // 13. Build the HTTP response (mask opponent hole cards)
        return buildResponse(hand, table, seatStates, dealerSeatNo, sbSeatNo, bbSeatNo,
                             requestingPlayerId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the dealer seat number for the new hand by rotating clockwise from
     * the previous hand's dealer.  For the very first hand, the seat with the
     * lowest seat number is chosen.
     */
    private int chooseDealerSeat(UUID tableId, List<TableSeat> activeSeats) {
        List<Hand> recent = handRepo.findByTableIdOrderByStartedAtDesc(tableId);
        if (recent.isEmpty()) {
            return activeSeats.get(0).getSeatNo();
        }
        return nextSeatNo(activeSeats, recent.get(0).getDealerSeat());
    }

    /**
     * Returns the seat number of the next active seat clockwise after
     * {@code currentSeatNo}, wrapping around if necessary.
     */
    private int nextSeatNo(List<TableSeat> activeSeats, int currentSeatNo) {
        for (TableSeat seat : activeSeats) {
            if (seat.getSeatNo() > currentSeatNo) {
                return seat.getSeatNo();
            }
        }
        return activeSeats.get(0).getSeatNo(); // wrap around
    }

    private TableSeat findSeatBySeatNo(List<TableSeat> activeSeats, int seatNo) {
        return activeSeats.stream()
            .filter(s -> s.getSeatNo() == seatNo)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Active seat " + seatNo + " not found — data inconsistency"));
    }

    /**
     * Deducts up to {@code amount} chips from the seat's stack (all-in if short).
     *
     * @return the actual chips posted
     */
    private int postBlind(TableSeat seat, int amount) {
        int posted = Math.min(amount, seat.getStackChips());
        seat.setStackChips(seat.getStackChips() - posted);
        return posted;
    }

    /**
     * Deals 2 hole cards to each active seat in two rounds (one card per round,
     * left-to-right seat order) and returns a map of seatNo → [card1, card2].
     */
    private Map<Integer, List<String>> dealHoleCards(List<TableSeat> activeSeats,
                                                      List<Card> deck) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (TableSeat seat : activeSeats) {
            result.put(seat.getSeatNo(), new ArrayList<>());
        }
        for (int round = 0; round < 2; round++) {
            for (TableSeat seat : activeSeats) {
                result.get(seat.getSeatNo()).add(deck.remove(0).toString());
            }
        }
        return result;
    }

    private List<SeatState> buildSeatStates(List<TableSeat> activeSeats,
                                             Map<Integer, List<String>> holeCards) {
        return activeSeats.stream()
            .map(seat -> new SeatState(
                seat.getSeatNo(),
                seat.getPlayer().getId(),
                seat.getPlayer().getUsername(),
                seat.getStackChips(),
                holeCards.get(seat.getSeatNo()),
                false,
                false
            ))
            .toList();
    }

    private String serialise(SnapshotPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise snapshot payload", e);
        }
    }

    private HandResponse buildResponse(Hand hand, PokerTable table,
                                        List<SeatState> seatStates,
                                        int dealerSeat, int sbSeat, int bbSeat,
                                        UUID requestingPlayerId) {
        List<String> myHoleCards = null;
        List<HandResponse.SeatView> seats = new ArrayList<>();

        for (SeatState state : seatStates) {
            boolean isMe = state.playerId().equals(requestingPlayerId);
            if (isMe) {
                myHoleCards = state.holeCards();
            }
            List<String> visibleCards = isMe
                ? state.holeCards()
                : List.of("**", "**");

            seats.add(new HandResponse.SeatView(
                state.seatNo(),
                state.playerId(),
                state.username(),
                state.stackChips(),
                visibleCards,
                state.folded(),
                state.allIn()
            ));
        }

        return new HandResponse(
            hand.getId(),
            table.getId(),
            hand.getStreet().name(),
            hand.getPotChips(),
            dealerSeat,
            sbSeat,
            bbSeat,
            myHoleCards,
            seats
        );
    }
}
