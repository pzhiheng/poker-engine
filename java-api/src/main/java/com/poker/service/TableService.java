package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.entity.TableSeat;
import com.poker.domain.repository.PlayerRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.TableSeatRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.web.dto.CreateTableRequest;
import com.poker.web.dto.JoinSeatRequest;
import com.poker.web.dto.SeatResponse;
import com.poker.web.dto.TableResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application-layer service for table management.
 *
 * <p>All public methods run inside a transaction. Read-only methods declare
 * {@code readOnly=true} so the persistence provider can skip dirty-checking.
 */
@Service
@Transactional
public class TableService {

    private final PokerTableRepository tableRepo;
    private final TableSeatRepository  seatRepo;
    private final PlayerRepository     playerRepo;

    public TableService(PokerTableRepository tableRepo,
                        TableSeatRepository  seatRepo,
                        PlayerRepository     playerRepo) {
        this.tableRepo  = tableRepo;
        this.seatRepo   = seatRepo;
        this.playerRepo = playerRepo;
    }

    // ── Create table ──────────────────────────────────────────────────────────

    /**
     * Creates a new poker table.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>bigBlind must equal 2 × smallBlind (checked in entity constructor)</li>
     *   <li>Table name must be unique (checked against DB)</li>
     * </ul>
     *
     * @param req validated request
     * @return the persisted table as a response DTO
     * @throws IllegalArgumentException  if blind relationship is invalid
     * @throws BusinessRuleException     if name is already taken
     */
    public TableResponse createTable(CreateTableRequest req) {
        if (tableRepo.existsByName(req.name())) {
            throw new BusinessRuleException(
                "TABLE_NAME_TAKEN",
                "A table named '" + req.name() + "' already exists");
        }

        // Entity constructor validates bigBlind == smallBlind * 2
        PokerTable table = new PokerTable(req.name(), req.smallBlind(), req.bigBlind());
        table = tableRepo.save(table);
        return TableResponse.from(table);
    }

    // ── Join seat ─────────────────────────────────────────────────────────────

    /**
     * Places a player in a specific seat at a table, deducting the buy-in from
     * their bankroll and setting the seat's in-game stack.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>Table must exist</li>
     *   <li>Table must be {@code WAITING} (players may not join mid-hand)</li>
     *   <li>Requested seat must not already be occupied</li>
     *   <li>Player must not already be seated at this table</li>
     *   <li>Player's bankroll must cover the buy-in</li>
     * </ul>
     *
     * @param tableId UUID of the target table
     * @param req     validated join request
     * @return the newly created seat as a response DTO
     * @throws ResourceNotFoundException if the table or player does not exist
     * @throws BusinessRuleException     if any business rule is violated
     */
    public SeatResponse joinSeat(UUID tableId, JoinSeatRequest req) {

        PokerTable table = tableRepo.findById(tableId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Table not found: " + tableId));

        if (!table.getStatus().acceptsJoins()) {
            throw new BusinessRuleException(
                "TABLE_NOT_OPEN",
                "Table '" + table.getName() + "' is " + table.getStatus()
                    + " and is not accepting new players");
        }

        // Seat must be unoccupied
        seatRepo.findByTableIdAndSeatNo(tableId, req.seatNo()).ifPresent(existing -> {
            if (existing.isOccupied()) {
                throw new BusinessRuleException(
                    "SEAT_OCCUPIED",
                    "Seat " + req.seatNo() + " is already taken");
            }
        });

        Player player = playerRepo.findById(req.playerId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Player not found: " + req.playerId()));

        // A player may not sit at the same table twice
        seatRepo.findByTableIdAndPlayerId(tableId, player.getId()).ifPresent(s -> {
            throw new BusinessRuleException(
                "ALREADY_SEATED",
                "Player '" + player.getUsername() + "' is already seated at this table");
        });

        if (player.getBankrollChips() < req.buyIn()) {
            throw new BusinessRuleException(
                "INSUFFICIENT_BANKROLL",
                "Player '" + player.getUsername() + "' has " + player.getBankrollChips()
                    + " chips but needs " + req.buyIn() + " to buy in");
        }

        // Deduct buy-in from bankroll and create the seat
        player.deductChips(req.buyIn());
        playerRepo.save(player);

        TableSeat seat = new TableSeat(table, player, req.seatNo(), req.buyIn());
        seat = seatRepo.save(seat);
        return SeatResponse.from(seat);
    }
}
