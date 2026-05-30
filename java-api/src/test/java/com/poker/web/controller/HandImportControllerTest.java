package com.poker.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.domain.model.HandSource;
import com.poker.domain.model.ImportStatus;
import com.poker.domain.repository.HandImportRepository;
import com.poker.security.JwtAuthFilter;
import com.poker.security.JwtService;
import com.poker.service.HandImportService;
import com.poker.web.dto.HandImportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link HandImportController}.
 */
@WebMvcTest(HandImportController.class)
@Import({
    com.poker.config.SecurityConfig.class,
    com.poker.web.advice.GlobalExceptionHandler.class,
    JwtAuthFilter.class,
    JwtService.class
})
class HandImportControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired JwtService   jwtService;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean HandImportService    importService;
    @MockitoBean HandImportRepository importRepo;

    String bearerToken;
    UUID   playerId;
    UUID   importId;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        importId = UUID.randomUUID();
        bearerToken = "Bearer " + jwtService.generateToken(playerId, "alice");
    }

    // ── POST /import/hands ────────────────────────────────────────────────────

    @Test
    void importHands_validFile_returns201WithSummary() throws Exception {
        HandImportResponse resp = new HandImportResponse(
            importId, "test.txt", "POKERSTARS", 3, 3,
            ImportStatus.DONE.name(), null, Instant.now());
        when(importService.importHands(eq(playerId), eq(HandSource.POKERSTARS), any()))
            .thenReturn(resp);

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "PokerStars Hand #1: ...".getBytes());

        mockMvc.perform(multipart("/import/hands")
                .file(file)
                .param("source", "POKERSTARS")
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.importId").value(importId.toString()))
            .andExpect(jsonPath("$.handsParsed").value(3))
            .andExpect(jsonPath("$.handsImported").value(3))
            .andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.source").value("POKERSTARS"));
    }

    @Test
    void importHands_requiresAuth_returns4xxWithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "data".getBytes());

        // No JWT → anonymous user lacks ROLE_PLAYER → Spring returns 403
        mockMvc.perform(multipart("/import/hands")
                .file(file)
                .param("source", "POKERSTARS"))
            .andExpect(status().isForbidden());
    }

    // ── GET /import/hands ─────────────────────────────────────────────────────

    @Test
    void listImports_returnsJobsForCaller() throws Exception {
        // Repo returns entities; service maps them — empty list verifies 200 + array shape
        when(importRepo.findByPlayerIdOrderByImportedAtDesc(playerId))
            .thenReturn(List.of());

        mockMvc.perform(get("/import/hands")
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listImports_requiresAuth() throws Exception {
        // No JWT → Spring returns 403 (anonymous user lacks ROLE_PLAYER)
        mockMvc.perform(get("/import/hands"))
            .andExpect(status().isForbidden());
    }

    // ── GET /import/hands/{id} ────────────────────────────────────────────────

    @Test
    void getImport_notFound_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(importRepo.findById(unknown)).thenReturn(Optional.empty());

        mockMvc.perform(get("/import/hands/{id}", unknown)
                .header(HttpHeaders.AUTHORIZATION, bearerToken))
            .andExpect(status().isNotFound());
    }
}
