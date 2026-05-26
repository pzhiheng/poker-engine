package com.poker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Reads the {@code Authorization: Bearer <token>} header, validates the JWT,
 * and populates the {@link SecurityContextHolder} for the duration of the request.
 *
 * <p>Requests without a valid token pass through unauthenticated — protected
 * endpoints reject them downstream via Spring Security's access rules.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims   = jwtService.validateToken(token);
                UUID   playerId = jwtService.extractPlayerId(claims);
                String username = jwtService.extractUsername(claims);

                // Store playerId as principal so controllers can retrieve it
                var auth = new UsernamePasswordAuthenticationToken(
                    playerId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))
                );
                auth.setDetails(username);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (JwtException e) {
                log.warn("Invalid JWT [{}]: {}", request.getRequestURI(), e.getMessage());
                // Fall through: SecurityContext stays empty → 401 from access rules
            }
        }

        chain.doFilter(request, response);
    }
}
