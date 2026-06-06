package com.poker.web.controller;

import com.poker.domain.model.TableStatus;
import com.poker.service.BotPlayerService;
import com.poker.service.HandService;
import com.poker.service.TableService;
import com.poker.web.dto.CreateTableRequest;
import com.poker.web.dto.HandResponse;
import com.poker.web.dto.JoinSeatRequest;
import com.poker.web.dto.SeatResponse;
import com.poker.web.dto.TableDetailResponse;
import com.poker.web.dto.TableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for table management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /tables}             — list all tables (optional ?status= filter)</li>
 *   <li>{@code GET  /tables/{id}}         — get one table with its seat list</li>
 *   <li>{@code POST /tables}             — create a new table</li>
 *   <li>{@code POST /tables/{id}/seats}  — seat a player at an existing table</li>
 * </ul>
 *
 * <p>Validation errors (400), business-rule violations (422), and not-found
 * cases (404) are all handled by {@link com.poker.web.advice.GlobalExceptionHandler}.
 */
@Tag(name = "Tables", description = "Poker table management — browse, create, and join seats")
@RestController
@RequestMapping("/tables")
public class TableController {

    private final TableService    tableService;
    private final BotPlayerService botPlayerService;
    private final HandService     handService;

    public TableController(TableService tableService, BotPlayerService botPlayerService,
                           HandService handService) {
        this.tableService    = tableService;
        this.botPlayerService = botPlayerService;
        this.handService     = handService;
    }

    // ── GET /tables ───────────────────────────────────────────────────────────

    @Operation(summary = "List tables",
               description = "Returns all tables, optionally filtered by status (WAITING / IN_HAND). Public endpoint — no JWT required.")
    @ApiResponse(responseCode = "200", description = "Table list (may be empty)")
    @GetMapping
    public List<TableResponse> listTables(
            @Parameter(description = "Filter by table status")
            @RequestParam(required = false) TableStatus status) {
        return tableService.listTables(status);
    }

    // ── GET /tables/{id} ──────────────────────────────────────────────────────

    @Operation(summary = "Get table details",
               description = "Returns a table together with its full seat list. Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Table found")
    @ApiResponse(responseCode = "404", description = "Table not found")
    @GetMapping("/{id}")
    public TableDetailResponse getTable(
            @Parameter(description = "Table UUID") @PathVariable UUID id) {
        return tableService.getTable(id);
    }

    // ── POST /tables ──────────────────────────────────────────────────────────

    @Operation(summary = "Create a table",
               description = "Creates a new WAITING poker table. bigBlind must equal 2 × smallBlind. **Requires JWT.**")
    @ApiResponse(responseCode = "201", description = "Table created")
    @ApiResponse(responseCode = "400", description = "Validation error (blind constraints)")
    @ApiResponse(responseCode = "422", description = "Business rule violation (e.g. bigBlind ≠ 2×smallBlind)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse createTable(@Valid @RequestBody CreateTableRequest req) {
        return tableService.createTable(req);
    }

    // ── POST /tables/{id}/seats ───────────────────────────────────────────────

    @Operation(summary = "Join a seat",
               description = "Seats a player at an existing WAITING table, deducting the buy-in from their bankroll. **Requires JWT.**")
    @ApiResponse(responseCode = "201", description = "Seat taken")
    @ApiResponse(responseCode = "404", description = "Table or player not found")
    @ApiResponse(responseCode = "422", description = "Business rule violation (seat taken, insufficient bankroll, etc.)")
    @PostMapping("/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse joinSeat(
            @Parameter(description = "Table UUID") @PathVariable UUID id,
            @Valid @RequestBody JoinSeatRequest req) {
        return tableService.joinSeat(id, req);
    }

    // ── POST /tables/{id}/bots ────────────────────────────────────────────────

    @Operation(summary = "Add a bot to the table",
               description = "Seats the built-in bot player at the specified seat. Body: {\"seatNo\": 2, \"buyIn\": 500}. **Requires JWT.**")
    @ApiResponse(responseCode = "201", description = "Bot seated")
    @ApiResponse(responseCode = "422", description = "Seat taken or table not WAITING")
    @PostMapping("/{id}/bots")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse addBot(
            @Parameter(description = "Table UUID") @PathVariable UUID id,
            @RequestBody AddBotRequest req) {
        JoinSeatRequest joinReq = new JoinSeatRequest(
            botPlayerService.getBotPlayerId(), req.seatNo(), req.buyIn());
        return tableService.joinSeat(id, joinReq);
    }

    // ── GET /tables/{id}/hand ─────────────────────────────────────────────────

    @Operation(summary = "Get current active hand",
               description = "Returns the active hand state for the table, with hole cards personalised for the requester. Used to restore state after a page reload. **Requires JWT.** Returns 422 if no hand is in progress.")
    @ApiResponse(responseCode = "200", description = "Current hand state")
    @ApiResponse(responseCode = "422", description = "No hand currently in progress")
    @GetMapping("/{id}/hand")
    public HandResponse getCurrentHand(
            @Parameter(description = "Table UUID") @PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID playerId) {
        return handService.getCurrentHand(id, playerId);
    }

    /** Minimal request body for adding a bot — no playerId needed. */
    public record AddBotRequest(int seatNo, int buyIn) {}
}
