package com.poker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration — stateless REST API baseline.
 *
 * <p>Phase 1 (this file): no JWT yet; all endpoints are open so that
 * Day 5 table endpoints can be exercised without auth headers.
 *
 * <p>Phase 2 (Day 7): add {@code JwtAuthenticationFilter}, restrict
 * everything except {@code /auth/**} and the Actuator health/metrics paths.
 *
 * <p>Design notes:
 * <ul>
 *   <li>CSRF is disabled — the API is stateless; there are no session cookies
 *       to cross-site-request-forge.</li>
 *   <li>Session creation is set to STATELESS — Spring Security must not
 *       create or use an HTTP session.</li>
 *   <li>{@link PasswordEncoder} is declared here so it can be injected by
 *       any service without a circular dependency on the auth layer.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── No CSRF for a stateless REST API ─────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Stateless — never create a session ───────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization rules ───────────────────────────────────────────
            // TODO (Day 7): narrow to .requestMatchers("/auth/**").permitAll()
            //               .anyRequest().authenticated()
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }

    /**
     * BCrypt password encoder — used by the registration service to hash
     * passwords before storage.  Strength 12 is the Spring Security default.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
