package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.repository.PlayerRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.security.JwtProperties;
import com.poker.security.JwtService;
import com.poker.web.dto.AuthResponse;
import com.poker.web.dto.LoginRequest;
import com.poker.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService} — mocked repository, real BCrypt + JwtService.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock PlayerRepository playerRepo;

    // Real collaborators — we want to test the actual encoding and token logic
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    JwtService      jwtService      = new JwtService(
        new JwtProperties("test-secret-that-is-long-enough-32c", 86_400_000L));

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(playerRepo, passwordEncoder, jwtService);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_newUser_savesPlayerAndReturnsToken() throws Exception {
        when(playerRepo.existsByUsername("alice")).thenReturn(false);
        when(playerRepo.save(any(Player.class))).thenAnswer(inv -> {
            Player p = inv.getArgument(0);
            var f = Player.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, UUID.randomUUID());
            return p;
        });

        AuthResponse resp = authService.register(new RegisterRequest("alice", "password123"));

        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.token()).isNotBlank();
        assertThat(resp.playerId()).isNotNull();
        verify(playerRepo).save(any(Player.class));
    }

    @Test
    void register_duplicateUsername_throws422() {
        when(playerRepo.existsByUsername("alice")).thenReturn(true);

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> authService.register(new RegisterRequest("alice", "password123")))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("USERNAME_TAKEN");
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_correctCredentials_returnsToken() throws Exception {
        UUID   id     = UUID.randomUUID();
        String hash   = passwordEncoder.encode("password123");
        Player player = new Player("alice", hash, 1_000);
        var f = Player.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(player, id);

        when(playerRepo.findByUsername("alice")).thenReturn(Optional.of(player));

        AuthResponse resp = authService.login(new LoginRequest("alice", "password123"));

        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.playerId()).isEqualTo(id);
        assertThat(resp.token()).isNotBlank();
    }

    @Test
    void login_unknownUsername_throws404() {
        when(playerRepo.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> authService.login(new LoginRequest("nobody", "pass")));
    }

    @Test
    void login_wrongPassword_throws422() throws Exception {
        Player player = new Player("alice", passwordEncoder.encode("correct"), 1_000);
        var f = Player.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(player, UUID.randomUUID());

        when(playerRepo.findByUsername("alice")).thenReturn(Optional.of(player));

        assertThatExceptionOfType(BusinessRuleException.class)
            .isThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
            .extracting(BusinessRuleException::getCode)
            .isEqualTo("INVALID_CREDENTIALS");
    }
}
