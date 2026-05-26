package com.poker.web.controller;

import com.poker.domain.model.TableStatus;
import com.poker.service.TableService;
import com.poker.web.dto.CreateTableRequest;
import com.poker.web.dto.JoinSeatRequest;
import com.poker.web.dto.SeatResponse;
import com.poker.web.dto.TableDetailResponse;
import com.poker.web.dto.TableResponse;
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
@RestController
@RequestMapping("/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    // ── GET /tables ───────────────────────────────────────────────────────────

    /**
     * Lists all tables, with an optional status filter.
     *
     * <p>Example: {@code GET /tables?status=WAITING}
     *
     * @param status optional; when present only tables with this status are returned
     * @return 200 OK with a (possibly empty) list of table summaries
     */
    @GetMapping
    public List<TableResponse> listTables(
            @RequestParam(required = false) TableStatus status) {
        return tableService.listTables(status);
    }

    // ── GET /tables/{id} ──────────────────────────────────────────────────────

    /**
     * Returns full detail for a single table, including all seats.
     *
     * @param id UUID of the requested table
     * @return 200 OK with the table and its seat list
     */
    @GetMapping("/{id}")
    public TableDetailResponse getTable(@PathVariable UUID id) {
        return tableService.getTable(id);
    }

    // ── POST /tables ──────────────────────────────────────────────────────────

    /**
     * Creates a new poker table.
     *
     * @param req validated request body
     * @return 201 Created with the new table representation
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse createTable(@Valid @RequestBody CreateTableRequest req) {
        return tableService.createTable(req);
    }

    // ── POST /tables/{id}/seats ───────────────────────────────────────────────

    /**
     * Seats a player at a specific position on an existing table.
     *
     * <p>Deducts the buy-in from the player's bankroll and creates the seat
     * row with the corresponding in-game stack.
     *
     * @param id  UUID of the target table (path variable)
     * @param req validated request body
     * @return 201 Created with the new seat representation
     */
    @PostMapping("/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse joinSeat(@PathVariable UUID id,
                                 @Valid @RequestBody JoinSeatRequest req) {
        return tableService.joinSeat(id, req);
    }
}
