-- ─────────────────────────────────────────────────────────────────────────────
-- V1__init.sql  –  Initial schema for the poker-engine java-api
-- ─────────────────────────────────────────────────────────────────────────────
-- Naming conventions:
--   tables     : snake_case plural
--   columns    : snake_case
--   PKs        : id  (UUID, server-generated)
--   FKs        : <referenced_table_singular>_id
--   indexes    : idx_<table>_<column(s)>
--   constraints: chk_<table>_<rule>, uq_<table>_<column(s)>
-- ─────────────────────────────────────────────────────────────────────────────

-- pgcrypto provides gen_random_uuid() used in DEFAULT expressions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── players ──────────────────────────────────────────────────────────────────
CREATE TABLE players (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    username        VARCHAR(50)  NOT NULL,
    password_hash   TEXT         NOT NULL,
    bankroll_chips  INT          NOT NULL DEFAULT 1000,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_players       PRIMARY KEY (id),
    CONSTRAINT uq_players_name  UNIQUE      (username),
    CONSTRAINT chk_players_bankroll CHECK (bankroll_chips >= 0)
);

-- ─── tables ───────────────────────────────────────────────────────────────────
CREATE TABLE tables (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    small_blind INT          NOT NULL,
    big_blind   INT          NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'WAITING',

    CONSTRAINT pk_tables        PRIMARY KEY (id),
    CONSTRAINT uq_tables_name   UNIQUE      (name),
    CONSTRAINT chk_tables_blinds  CHECK (big_blind = small_blind * 2),
    CONSTRAINT chk_tables_status  CHECK (status IN ('WAITING','IN_HAND','CLOSED'))
);

-- ─── table_seats ──────────────────────────────────────────────────────────────
CREATE TABLE table_seats (
    id          UUID      NOT NULL DEFAULT gen_random_uuid(),
    table_id    UUID      NOT NULL,
    player_id   UUID,                        -- NULL = empty seat
    seat_no     INT       NOT NULL,
    stack_chips INT       NOT NULL DEFAULT 0,
    sitting_out BOOLEAN   NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_table_seats              PRIMARY KEY (id),
    CONSTRAINT fk_table_seats_table        FOREIGN KEY (table_id)  REFERENCES tables(id),
    CONSTRAINT fk_table_seats_player       FOREIGN KEY (player_id) REFERENCES players(id),
    CONSTRAINT uq_table_seats_table_seat   UNIQUE      (table_id, seat_no),
    CONSTRAINT chk_table_seats_seat_no     CHECK       (seat_no BETWEEN 1 AND 6),
    CONSTRAINT chk_table_seats_stack       CHECK       (stack_chips >= 0)
);

-- ─── hands ────────────────────────────────────────────────────────────────────
CREATE TABLE hands (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    table_id    UUID        NOT NULL,
    dealer_seat INT         NOT NULL,
    street      VARCHAR(20) NOT NULL DEFAULT 'PREFLOP',
    status      VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    pot_chips   INT         NOT NULL DEFAULT 0,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hands          PRIMARY KEY (id),
    CONSTRAINT fk_hands_table    FOREIGN KEY (table_id) REFERENCES tables(id),
    CONSTRAINT chk_hands_street  CHECK (street IN ('PREFLOP','FLOP','TURN','RIVER','SHOWDOWN')),
    CONSTRAINT chk_hands_status  CHECK (status IN ('WAITING','IN_PROGRESS','SHOWDOWN','FINISHED')),
    CONSTRAINT chk_hands_pot     CHECK (pot_chips >= 0)
);

-- ─── hand_actions ─────────────────────────────────────────────────────────────
CREATE TABLE hand_actions (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    hand_id      UUID        NOT NULL,
    player_id    UUID        NOT NULL,
    action_type  VARCHAR(20) NOT NULL,
    amount       INT         NOT NULL DEFAULT 0,
    action_order INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hand_actions           PRIMARY KEY (id),
    CONSTRAINT fk_hand_actions_hand      FOREIGN KEY (hand_id)   REFERENCES hands(id),
    CONSTRAINT fk_hand_actions_player    FOREIGN KEY (player_id) REFERENCES players(id),
    CONSTRAINT chk_hand_actions_type     CHECK (action_type IN ('FOLD','CHECK','CALL','BET','RAISE','ALL_IN')),
    CONSTRAINT chk_hand_actions_amount   CHECK (amount >= 0)
);

-- ─── hand_snapshots ───────────────────────────────────────────────────────────
CREATE TABLE hand_snapshots (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    hand_id    UUID        NOT NULL,
    version_no INT         NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hand_snapshots              PRIMARY KEY (id),
    CONSTRAINT fk_hand_snapshots_hand         FOREIGN KEY (hand_id) REFERENCES hands(id),
    CONSTRAINT uq_hand_snapshots_hand_version UNIQUE      (hand_id, version_no)
);

-- ─── pot_results ──────────────────────────────────────────────────────────────
CREATE TABLE pot_results (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    hand_id          UUID         NOT NULL,
    winner_player_id UUID,                   -- NULL allowed for edge cases (returned bet)
    chips_awarded    INT          NOT NULL,
    reason           VARCHAR(120) NOT NULL,

    CONSTRAINT pk_pot_results          PRIMARY KEY (id),
    CONSTRAINT fk_pot_results_hand     FOREIGN KEY (hand_id)          REFERENCES hands(id),
    CONSTRAINT fk_pot_results_winner   FOREIGN KEY (winner_player_id) REFERENCES players(id),
    CONSTRAINT chk_pot_results_chips   CHECK (chips_awarded >= 0)
);

-- ─── indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_table_seats_table    ON table_seats   (table_id);
CREATE INDEX idx_table_seats_player   ON table_seats   (player_id);

CREATE INDEX idx_hands_table          ON hands         (table_id);
CREATE INDEX idx_hands_status         ON hands         (table_id, status);

CREATE INDEX idx_hand_actions_hand    ON hand_actions  (hand_id);
CREATE INDEX idx_hand_actions_order   ON hand_actions  (hand_id, action_order);

CREATE INDEX idx_hand_snapshots_hand  ON hand_snapshots(hand_id);
CREATE INDEX idx_hand_snapshots_ver   ON hand_snapshots(hand_id, version_no DESC);

CREATE INDEX idx_pot_results_hand     ON pot_results   (hand_id);
