package com.poker.web.controller;

import com.poker.service.AuthService;
import com.poker.web.dto.AuthResponse;
import com.poker.web.dto.LoginRequest;
import com.poker.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for player authentication.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /auth/register} — create account, returns JWT</li>
 *   <li>{@code POST /auth/login}    — authenticate, returns JWT</li>
 * </ul>
 *
 * <p>Both endpoints are public (no token required) — see
 * {@link com.poker.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new player and returns a signed JWT.
     *
     * @return 201 Created with {@link AuthResponse}
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    /**
     * Authenticates an existing player and returns a fresh JWT.
     *
     * @return 200 OK with {@link AuthResponse}
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
