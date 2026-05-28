package com.poker.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.security.JwtAuthFilter;
import com.poker.security.JwtService;
import com.poker.service.HandService;
import com.poker.web.dto.HandResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link HandController}.
 */
@WebMvcTest(HandController.class)
@Import({
    com.poker.config.SecurityConfig.class,
    com.poker.web.advice.GlobalExceptionHandler.class,
    JwtAuthFilter.class,
    JwtService.class
})
class HandControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService   jwtService;

    @MockitoBean HandService handService;

    private String bearerToken;
    private UUID   playerId;

    @BeforeEach
    void setUp() {
        playerId    = UUID.randomUUID();
        bearerToken = "Bearer " + jwtService.generateToken(playerId, "testuser");
    }

    // ── POST /tables/{id}/hands ───────────────────────────────────────────────

    @Test
    void startHand_returns201WithBody() throws Exception {
        UUID tableId = UUID.randomUUID();
        UUID handId  = UUID.randomUUID();

        HandResponse.SeatView seatA = new HandResponse.SeatView(
            1, playerId, "testuser", 495, List.of("Ah", "Kh"), false, false);
        HandResponse.SeatView seatB = new HandResponse.SeatView(
            2, UUID.randomUUID(), "bob", 490, List.of("**", "**"), false, false);

        HandResponse resp = new HandResponse(
            handId, tableId, "PREFLOP", 15, 1, 1, 2,
            List.of("Ah", "Kh"), List.of(seatA, seatB));

        when(handService.startHand(eq(tableId), any(UUID.class))).thenReturn(resp);

        mockMvc.perform(post("/tables/{id}/hands", tableId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.handId").value(handId.toString()))
            .andExpect(jsonPath("$.street").value("PREFLOP"))
            .andExpect(jsonPath("$.potChips").value(15))
            .andExpect(jsonPath("$.dealerSeat").value(1))
            .andExpect(jsonPath("$.sbSeat").value(1))
            .andExpect(jsonPath("$.bbSeat").value(2))
            .andExpect(jsonPath("$.myHoleCards[0]").value("Ah"))
            .andExpect(jsonPath("$.myHoleCards[1]").value("Kh"))
            .andExpect(jsonPath("$.seats").isArray())
            .andExpect(jsonPath("$.seats.length()").value(2))
            .andExpect(jsonPath("$.seats[1].holeCards[0]").value("**"));
    }

    @Test
    void startHand_noToken_returns403() throws Exception {
        UUID tableId = UUID.randomUUID();

        mockMvc.perform(post("/tables/{id}/hands", tableId))
            .andExpect(status().isForbidden());
    }

    @Test
    void startHand_tableNotFound_returns404() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(handService.startHand(eq(tableId), any(UUID.class)))
            .thenThrow(new ResourceNotFoundException("Table not found: " + tableId));

        mockMvc.perform(post("/tables/{id}/hands", tableId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void startHand_tableNotWaiting_returns422() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(handService.startHand(eq(tableId), any(UUID.class)))
            .thenThrow(new BusinessRuleException("TABLE_NOT_WAITING", "table is IN_HAND"));

        mockMvc.perform(post("/tables/{id}/hands", tableId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TABLE_NOT_WAITING"));
    }

    @Test
    void startHand_handAlreadyActive_returns422() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(handService.startHand(eq(tableId), any(UUID.class)))
            .thenThrow(new BusinessRuleException("HAND_ALREADY_ACTIVE", "hand in progress"));

        mockMvc.perform(post("/tables/{id}/hands", tableId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("HAND_ALREADY_ACTIVE"));
    }

    @Test
    void startHand_insufficientPlayers_returns422() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(handService.startHand(eq(tableId), any(UUID.class)))
            .thenThrow(new BusinessRuleException("INSUFFICIENT_PLAYERS", "need 2 players"));

        mockMvc.perform(post("/tables/{id}/hands", tableId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_PLAYERS"));
    }
}
