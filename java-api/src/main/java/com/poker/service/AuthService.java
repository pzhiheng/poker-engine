package com.poker.service;

import com.poker.domain.entity.Player;
import com.poker.domain.repository.PlayerRepository;
import com.poker.exception.BusinessRuleException;
import com.poker.exception.ResourceNotFoundException;
import com.poker.security.JwtService;
import com.poker.web.dto.AuthResponse;
import com.poker.web.dto.LoginRequest;
import com.poker.web.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles player registration and login, issuing JWT tokens on success.
 */
@Service
@Transactional
public class AuthService {

    private final PlayerRepository playerRepo;
    private final PasswordEncoder  passwordEncoder;
    private final JwtService       jwtService;

    public AuthService(PlayerRepository playerRepo,
                       PasswordEncoder  passwordEncoder,
                       JwtService       jwtService) {
        this.playerRepo      = playerRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

    // ── Register ──────────────────────────────────────────────────────────────

    /**
     * Creates a new player account and returns a JWT.
     *
     * <p>The plaintext password is never stored — only its BCrypt digest.
     * New players start with the default bankroll of 1 000 chips.
     *
     * @throws BusinessRuleException if the username is already taken
     */
    public AuthResponse register(RegisterRequest req) {
        if (playerRepo.existsByUsername(req.username())) {
            throw new BusinessRuleException(
                "USERNAME_TAKEN",
                "Username '" + req.username() + "' is already registered");
        }

        Player player = new Player(
            req.username(),
            passwordEncoder.encode(req.password()),
            1_000
        );
        player = playerRepo.save(player);

        String token = jwtService.generateToken(player.getId(), player.getUsername());
        return new AuthResponse(player.getId(), player.getUsername(), token);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a player by username + password and returns a fresh JWT.
     *
     * @throws ResourceNotFoundException if no player with that username exists
     * @throws BusinessRuleException     if the password is wrong
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Player player = playerRepo.findByUsername(req.username())
            .orElseThrow(() -> new ResourceNotFoundException(
                "No account found for username '" + req.username() + "'"));

        if (!passwordEncoder.matches(req.password(), player.getPasswordHash())) {
            throw new BusinessRuleException(
                "INVALID_CREDENTIALS",
                "Incorrect password");
        }

        String token = jwtService.generateToken(player.getId(), player.getUsername());
        return new AuthResponse(player.getId(), player.getUsername(), token);
    }
}
