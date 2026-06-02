package com.poker.web.controller;

import com.poker.service.AuthService;
import com.poker.web.dto.AuthResponse;
import com.poker.web.dto.LoginRequest;
import com.poker.web.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Player registration and login — both endpoints are public (no JWT required)")
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
    @Operation(summary = "Register a new player",
               description = "Creates a player account and returns a JWT. Username must be 3–50 chars; password ≥ 8 chars.")
    @ApiResponse(responseCode = "201", description = "Player registered — JWT included in response")
    @ApiResponse(responseCode = "409", description = "Username already taken")
    @ApiResponse(responseCode = "400", description = "Validation error (username/password constraints)")
    @SecurityRequirements   // explicitly mark as public (overrides the global Bearer requirement)
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
    @Operation(summary = "Login",
               description = "Validates credentials and returns a JWT. Use the token as `Authorization: Bearer <token>`.")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @ApiResponse(responseCode = "401", description = "Invalid username or password")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
