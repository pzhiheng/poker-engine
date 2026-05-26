package com.poker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that Flyway V1__init.sql produces the exact schema the JPA entities
 * expect: all tables, CHECK constraints, UNIQUE constraints, and indexes.
 *
 * <p>These tests run against a real PostgreSQL container (via Testcontainers).
 * They are skipped automatically when no Docker socket is reachable — so the
 * fast unit-test feedback loop is never broken on machines without Docker.
 *
 * <p>Note: we intentionally do NOT use {@code @Testcontainers(disabledWithoutDocker=true)}
 * because that annotation's Docker probe runs its own docker-java code path that
 * does not respect the {@code api.version} JVM property, causing false negatives
 * with Docker Engine ≥ 29 / Colima.  A plain file-existence check is sufficient.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Tables ──────────────────────────────────────────────────────────────

    /**
     * Skips the entire class when no Docker socket is present.
     * Checks both the standard path and the Colima path so this works on
     * Linux CI (/var/run/docker.sock) and macOS + Colima (~/.colima/...).
     */
    @BeforeAll
    static void requireDocker() {
        boolean dockerAvailable =
            new File("/var/run/docker.sock").exists() ||
            new File(System.getProperty("user.home") + "/.colima/default/docker.sock").exists();
        assumeTrue(dockerAvailable, "Skipping: no Docker socket found");
    }

    @ParameterizedTest(name = "table ''{0}'' exists")
    @ValueSource(strings = {
        "players", "tables", "table_seats",
        "hands", "hand_actions", "hand_snapshots", "pot_results"
    })
    void allTablesExist(String tableName) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = 'public' AND table_name = ?",
            Integer.class, tableName);
        assertEquals(1, count, "Table '" + tableName + "' is missing");
    }

    // ── Flyway bookkeeping ──────────────────────────────────────────────────

    @Test
    void onlyOneFlywayMigrationApplied() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
            Integer.class);
        assertEquals(1, count, "Expected exactly V1 in flyway_schema_history");
    }

    @Test
    void flywayV1HasCorrectDescription() {
        String description = jdbc.queryForObject(
            "SELECT description FROM flyway_schema_history WHERE version = '1'",
            String.class);
        assertEquals("init", description);
    }

    // ── Column presence spot-checks ─────────────────────────────────────────

    @Test
    void playersTableHasExpectedColumns() {
        assertColumnsExist("players",
            "id", "username", "password_hash", "bankroll_chips", "created_at");
    }

    @Test
    void handsTableHasExpectedColumns() {
        assertColumnsExist("hands",
            "id", "table_id", "dealer_seat", "street", "status", "pot_chips", "started_at");
    }

    @Test
    void handSnapshotsPayloadIsJsonb() {
        String dataType = jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns "
            + "WHERE table_name = 'hand_snapshots' AND column_name = 'payload'",
            String.class);
        assertEquals("jsonb", dataType, "hand_snapshots.payload must be jsonb");
    }

    // ── Unique constraints ──────────────────────────────────────────────────

    @Test
    void playersUsernameIsUnique() {
        assertUniqueConstraintExists("players", "uq_players_name");
    }

    @Test
    void tablesNameIsUnique() {
        assertUniqueConstraintExists("tables", "uq_tables_name");
    }

    @Test
    void tableSeatsSeatNoIsUniquePerTable() {
        assertUniqueConstraintExists("table_seats", "uq_table_seats_table_seat");
    }

    @Test
    void handSnapshotsVersionIsUniquePerHand() {
        assertUniqueConstraintExists("hand_snapshots", "uq_hand_snapshots_hand_version");
    }

    // ── Indexes ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "index ''{0}'' exists")
    @ValueSource(strings = {
        "idx_table_seats_table",
        "idx_table_seats_player",
        "idx_hands_table",
        "idx_hands_status",
        "idx_hand_actions_hand",
        "idx_hand_actions_order",
        "idx_hand_snapshots_hand",
        "idx_hand_snapshots_ver",
        "idx_pot_results_hand"
    })
    void indexExists(String indexName) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes "
            + "WHERE schemaname = 'public' AND indexname = ?",
            Integer.class, indexName);
        assertEquals(1, count, "Index '" + indexName + "' is missing");
    }

    // ── CHECK constraints — insert rejection ───────────────────────────────

    @Test
    void playersRejectsNegativeBankroll() {
        assertThrows(Exception.class, () ->
            jdbc.update(
                "INSERT INTO players(username, password_hash, bankroll_chips) "
                + "VALUES ('bad', 'x', -1)"),
            "bankroll_chips < 0 should be rejected by chk_players_bankroll");
    }

    @Test
    void tableSeatsRejectsSeatOutOfRange() {
        // Need a parent table row first; use a raw insert to bypass the JPA layer
        jdbc.update("INSERT INTO tables(name, small_blind, big_blind, status) "
                    + "VALUES ('tmp', 1, 2, 'WAITING')");
        String tableId = jdbc.queryForObject(
            "SELECT id::text FROM tables WHERE name = 'tmp'", String.class);

        assertThrows(Exception.class, () ->
            jdbc.update(
                "INSERT INTO table_seats(table_id, seat_no, stack_chips) "
                + "VALUES (?::uuid, 7, 0)", tableId),
            "seat_no = 7 should be rejected by chk_table_seats_seat_no");
    }

    @Test
    void handsRejectsUnknownStreet() {
        jdbc.update("INSERT INTO tables(name, small_blind, big_blind, status) "
                    + "VALUES ('tmp2', 1, 2, 'WAITING')");
        String tableId = jdbc.queryForObject(
            "SELECT id::text FROM tables WHERE name = 'tmp2'", String.class);

        assertThrows(Exception.class, () ->
            jdbc.update(
                "INSERT INTO hands(table_id, dealer_seat, street, status) "
                + "VALUES (?::uuid, 1, 'MIDNIGHT', 'IN_PROGRESS')", tableId),
            "street = 'MIDNIGHT' should be rejected by chk_hands_street");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void assertColumnsExist(String table, String... columns) {
        for (String col : columns) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = ? AND column_name = ?",
                Integer.class, table, col);
            assertEquals(1, count,
                "Column '" + col + "' missing from table '" + table + "'");
        }
    }

    private void assertUniqueConstraintExists(String table, String constraintName) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints "
            + "WHERE table_name = ? AND constraint_name = ? AND constraint_type = 'UNIQUE'",
            Integer.class, table, constraintName);
        assertEquals(1, count,
            "Unique constraint '" + constraintName + "' missing on '" + table + "'");
    }
}
