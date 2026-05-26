package com.poker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.File;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test: verifies the full Spring application context loads cleanly
 * against a real PostgreSQL instance (via Testcontainers) with Flyway
 * migrations applied.
 *
 * <p>If this test is green, the following are all confirmed:
 * <ul>
 *   <li>All Spring beans wire without conflict</li>
 *   <li>Security configuration loads</li>
 *   <li>Flyway V1 migration runs without errors</li>
 *   <li>Hibernate schema validation passes (ddl-auto=validate)</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JavaApiApplicationTests {

    @BeforeAll
    static void requireDocker() {
        boolean dockerAvailable =
            new File("/var/run/docker.sock").exists() ||
            new File(System.getProperty("user.home") + "/.colima/default/docker.sock").exists();
        assumeTrue(dockerAvailable, "Skipping: no Docker socket found");
    }

    @Test
    void contextLoads() {
        // If the context starts, all of the above invariants hold.
    }
}
