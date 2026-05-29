package com.poker.config;

import com.poker.security.JwtAuthFilter;
import com.poker.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration — stateless JWT bearer token auth.
 *
 * <p>Access rules:
 * <ul>
 *   <li>{@code POST /auth/**}           — public (register + login)</li>
 *   <li>{@code GET  /tables/**}         — public (browse tables without logging in)</li>
 *   <li>{@code GET  /actuator/**}       — public (health + metrics)</li>
 *   <li>Everything else                 — requires {@code ROLE_PLAYER} (valid JWT)</li>
 * </ul>
 *
 * <p>CSRF is disabled — stateless JWTs carried in the {@code Authorization}
 * header are immune to cross-site request forgery (no cookie is used).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/tables/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/players/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().hasRole("PLAYER")
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
