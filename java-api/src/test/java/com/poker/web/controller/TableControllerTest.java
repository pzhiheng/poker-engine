package com.poker.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.model.TableStatus;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.service.TableService;
import com.poker.web.dto.CreateTableRequest;
import com.poker.web.dto.JoinSeatRequest;
import com.poker.web.dto.SeatResponse;
import com.poker.web.dto.TableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link TableController}.
 *
 * <p>Uses {@code @WebMvcTest} so only the web layer (filters, Jackson, validation,
 * {@code @ControllerAdvice}) loads — no Spring Data or Flyway context.
 * {@link TableService} is replaced by a Mockito mock.
 */
@WebMvcTest(TableController.class)
@Import({
    com.poker.config.SecurityConfig.class,
    com.poker.web.advice.GlobalExceptionHandler.class
})
class TableControllerTest {

    @Autowired MockMvc        mockMvc;
    @Autowired ObjectMapper   objectMapper;

    @MockitoBean TableService tableService;

    // ── POST /tables ──────────────────────────────────────────────────────────

    @Test
    void createTable_returns201WithBody() throws Exception {
        UUID id = UUID.randomUUID();
        TableResponse resp = new TableResponse(id, "Main Table", 5, 10, TableStatus.WAITING, 0);
        when(tableService.createTable(any(CreateTableRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Main Table","smallBlind":5,"bigBlind":10}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Main Table"))
            .andExpect(jsonPath("$.smallBlind").value(5))
            .andExpect(jsonPath("$.bigBlind").value(10))
            .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void createTable_missingName_returns400() throws Exception {
        mockMvc.perform(post("/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","smallBlind":5,"bigBlind":10}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createTable_duplicateName_returns422() throws Exception {
        when(tableService.createTable(any()))
            .thenThrow(new BusinessRuleException("TABLE_NAME_TAKEN", "already exists"));

        mockMvc.perform(post("/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Taken","smallBlind":5,"bigBlind":10}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TABLE_NAME_TAKEN"));
    }

    @Test
    void createTable_badBlinds_returns400() throws Exception {
        when(tableService.createTable(any()))
            .thenThrow(new IllegalArgumentException("bigBlind must be 2x smallBlind"));

        mockMvc.perform(post("/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Bad","smallBlind":5,"bigBlind":11}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ── POST /tables/{id}/seats ───────────────────────────────────────────────

    @Test
    void joinSeat_returns201WithBody() throws Exception {
        UUID tableId  = UUID.randomUUID();
        UUID seatId   = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        SeatResponse resp = new SeatResponse(seatId, tableId, playerId, 1, 500, false);
        when(tableService.joinSeat(eq(tableId), any(JoinSeatRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/tables/{id}/seats", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"playerId":"%s","seatNo":1,"buyIn":500}
                    """.formatted(playerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(seatId.toString()))
            .andExpect(jsonPath("$.seatNo").value(1))
            .andExpect(jsonPath("$.stackChips").value(500))
            .andExpect(jsonPath("$.playerId").value(playerId.toString()));
    }

    @Test
    void joinSeat_invalidSeatNo_returns400() throws Exception {
        UUID tableId  = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(post("/tables/{id}/seats", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"playerId":"%s","seatNo":7,"buyIn":500}
                    """.formatted(playerId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void joinSeat_tableNotFound_returns404() throws Exception {
        UUID tableId  = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        when(tableService.joinSeat(eq(tableId), any()))
            .thenThrow(new ResourceNotFoundException("Table not found"));

        mockMvc.perform(post("/tables/{id}/seats", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"playerId":"%s","seatNo":1,"buyIn":500}
                    """.formatted(playerId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void joinSeat_seatOccupied_returns422() throws Exception {
        UUID tableId  = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        when(tableService.joinSeat(eq(tableId), any()))
            .thenThrow(new BusinessRuleException("SEAT_OCCUPIED", "Seat 1 is taken"));

        mockMvc.perform(post("/tables/{id}/seats", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"playerId":"%s","seatNo":1,"buyIn":500}
                    """.formatted(playerId)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("SEAT_OCCUPIED"));
    }
}
