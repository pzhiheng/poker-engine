package com.poker.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.security.JwtAuthFilter;
import com.poker.security.JwtService;
import com.poker.service.AuthService;
import com.poker.web.dto.AuthResponse;
import com.poker.web.dto.LoginRequest;
import com.poker.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AuthController}.
 */
@WebMvcTest(AuthController.class)
@Import({
    com.poker.config.SecurityConfig.class,
    com.poker.web.advice.GlobalExceptionHandler.class,
    JwtAuthFilter.class,
    JwtService.class
})
class AuthControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;

    // ── POST /auth/register ───────────────────────────────────────────────────

    @Test
    void register_returns201WithToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.register(any(RegisterRequest.class)))
            .thenReturn(new AuthResponse(id, "alice", "tok.en.value"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"password123"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.playerId").value(id.toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.token").value("tok.en.value"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"short"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void register_duplicateUsername_returns422() throws Exception {
        when(authService.register(any()))
            .thenThrow(new BusinessRuleException("USERNAME_TAKEN", "alice is taken"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"password123"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    // ── POST /auth/login ──────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class)))
            .thenReturn(new AuthResponse(id, "alice", "tok.en.value"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("tok.en.value"));
    }

    @Test
    void login_unknownUser_returns404() throws Exception {
        when(authService.login(any()))
            .thenThrow(new ResourceNotFoundException("No account for 'nobody'"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"nobody","password":"password123"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void login_wrongPassword_returns422() throws Exception {
        when(authService.login(any()))
            .thenThrow(new BusinessRuleException("INVALID_CREDENTIALS", "bad password"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"wrongpass"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
