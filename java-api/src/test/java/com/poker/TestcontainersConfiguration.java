package com.poker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration for all integration tests.
 *
 * <p>Because Spring's test context cache keeps the application context alive
 * across test classes that share the same context key, the PostgreSQL container
 * declared here starts exactly once per test run — not once per test class.
 *
 * <p>Usage: annotate your test class with
 * {@code @Import(TestcontainersConfiguration.class)}.
 *
 * <p>{@code @ServiceConnection} wires the container's JDBC URL, username and
 * password directly into {@code spring.datasource.*} — no
 * {@code @DynamicPropertySource} needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
