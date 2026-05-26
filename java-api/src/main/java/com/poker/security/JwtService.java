package com.poker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT utility: signs tokens on login and validates them on every request.
 *
 * <p>Tokens carry the player's UUID as the subject and their username as a
 * custom claim.  They are signed with HMAC-SHA256 using the configured secret.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long      expirationMs;

    public JwtService(JwtProperties props) {
        this.key          = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.expirationMs();
    }

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Issues a signed JWT for the given player.
     *
     * @param playerId UUID of the authenticated player (becomes the {@code sub} claim)
     * @param username display name (stored as {@code username} claim)
     * @return compact, URL-safe JWT string
     */
    public String generateToken(UUID playerId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(playerId.toString())
            .claim("username", username)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expirationMs))
            .signWith(key)
            .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Parses and validates a compact JWT string.
     *
     * @param token compact JWT
     * @return parsed {@link Claims} if valid
     * @throws JwtException if the token is malformed, expired, or has a bad signature
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /** Extracts the player UUID from a validated token's subject claim. */
    public UUID extractPlayerId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /** Extracts the username from a validated token's custom claim. */
    public String extractUsername(Claims claims) {
        return claims.get("username", String.class);
    }
}
