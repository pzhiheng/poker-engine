package com.poker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code jwt.*} properties from {@code application.properties}.
 *
 * <p>Registered as a Spring bean via {@code @EnableConfigurationProperties}
 * in {@link com.poker.config.SecurityConfig}.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long   expirationMs
) {}
