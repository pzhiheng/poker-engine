package com.poker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtService} — no Spring context required.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Secret must be ≥ 32 chars for HMAC-SHA256
        JwtProperties props = new JwtProperties(
            "test-secret-that-is-long-enough-32c",
            86_400_000L   // 24 hours
        );
        jwtService = new JwtService(props);
    }

    @Test
    void generateAndValidate_roundTrip() {
        UUID   id       = UUID.randomUUID();
        String username = "alice";

        String  token  = jwtService.generateToken(id, username);
        Claims  claims = jwtService.validateToken(token);

        assertThat(jwtService.extractPlayerId(claims)).isEqualTo(id);
        assertThat(jwtService.extractUsername(claims)).isEqualTo(username);
    }

    @Test
    void validateToken_tamperedSignature_throwsJwtException() {
        String token   = jwtService.generateToken(UUID.randomUUID(), "bob");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatExceptionOfType(JwtException.class)
            .isThrownBy(() -> jwtService.validateToken(tampered));
    }

    @Test
    void validateToken_expiredToken_throwsJwtException() {
        JwtProperties shortLived = new JwtProperties(
            "test-secret-that-is-long-enough-32c",
            -1L   // already expired
        );
        JwtService shortService = new JwtService(shortLived);
        String token = shortService.generateToken(UUID.randomUUID(), "carol");

        assertThatExceptionOfType(JwtException.class)
            .isThrownBy(() -> jwtService.validateToken(token));
    }
}
