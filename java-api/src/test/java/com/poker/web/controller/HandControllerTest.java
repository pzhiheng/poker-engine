package com.poker.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.model.ActionFeedback;
import com.poker.domain.model.ActionType;
import com.poker.domain.model.FeedbackQuality;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.security.JwtAuthFilter;
import com.poker.security.JwtService;
import com.poker.service.HandService;
import com.poker.web.dto.ActionResponse;
import com.poker.web.dto.HandResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
            List.of(), List.of("Ah", "Kh"), List.of(seatA, seatB));

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

    // ── POST /hands/{id}/actions ──────────────────────────────────────────────

    @Test
    void recordAction_returns200WithFeedback() throws Exception {
        UUID handId = UUID.randomUUID();

        ActionFeedback feedback = new ActionFeedback(
            ActionType.CALL, ActionType.RAISE, 0.62, 0.28,
            FeedbackQuality.SUBOPTIMAL,
            "You had 62% equity vs 28% pot odds — raising for value would be stronger.",
            List.of());

        HandResponse.SeatView seatA = new HandResponse.SeatView(
            1, playerId, "testuser", 490, List.of("Ah", "Kh"), false, false);
        HandResponse.SeatView seatB = new HandResponse.SeatView(
            2, UUID.randomUUID(), "bob", 480, List.of("**", "**"), false, false);

        ActionResponse resp = new ActionResponse(
            handId, 2, "PREFLOP", 30, 2, List.of(), List.of(seatA, seatB), feedback);

        when(handService.recordAction(eq(handId), any(UUID.class), any()))
            .thenReturn(resp);

        mockMvc.perform(post("/hands/{id}/actions", handId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actionType":"CALL","amount":10}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.handId").value(handId.toString()))
            .andExpect(jsonPath("$.actionOrder").value(2))
            .andExpect(jsonPath("$.street").value("PREFLOP"))
            .andExpect(jsonPath("$.potChips").value(30))
            .andExpect(jsonPath("$.feedback.actionTaken").value("CALL"))
            .andExpect(jsonPath("$.feedback.recommendedAction").value("RAISE"))
            .andExpect(jsonPath("$.feedback.equity").value(0.62))
            .andExpect(jsonPath("$.feedback.potOdds").value(0.28))
            .andExpect(jsonPath("$.feedback.quality").value("SUBOPTIMAL"))
            .andExpect(jsonPath("$.feedback.explanation").isString());
    }

    @Test
    void recordAction_noToken_returns403() throws Exception {
        mockMvc.perform(post("/hands/{id}/actions", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actionType":"FOLD","amount":0}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void recordAction_missingActionType_returns400() throws Exception {
        mockMvc.perform(post("/hands/{id}/actions", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":10}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void recordAction_notYourTurn_returns422() throws Exception {
        UUID handId = UUID.randomUUID();
        when(handService.recordAction(eq(handId), any(UUID.class), any()))
            .thenThrow(new BusinessRuleException("NOT_YOUR_TURN", "not your turn"));

        mockMvc.perform(post("/hands/{id}/actions", handId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actionType":"CALL","amount":10}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("NOT_YOUR_TURN"));
    }

    @Test
    void recordAction_cannotCheck_returns422() throws Exception {
        UUID handId = UUID.randomUUID();
        when(handService.recordAction(eq(handId), any(UUID.class), any()))
            .thenThrow(new BusinessRuleException("CANNOT_CHECK", "bet to call"));

        mockMvc.perform(post("/hands/{id}/actions", handId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actionType":"CHECK","amount":0}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CANNOT_CHECK"));
    }
}
