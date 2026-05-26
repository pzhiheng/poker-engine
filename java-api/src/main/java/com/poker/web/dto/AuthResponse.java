package com.poker.web.dto;

import java.util.UUID;

/**
 * Returned by both {@code POST /auth/register} and {@code POST /auth/login}.
 *
 * <p>The client stores {@code token} and sends it as
 * {@code Authorization: Bearer <token>} on subsequent requests.
 */
public record AuthResponse(
    UUID   playerId,
    String username,
    String token
) {}
