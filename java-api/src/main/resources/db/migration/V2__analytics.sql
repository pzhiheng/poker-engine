-- ─────────────────────────────────────────────────────────────────────────────
-- V2 — Analytics schema
--
-- 1. Street tracking on hand_actions (needed for per-street stats: VPIP, PFR)
-- 2. Data-source tag on hands (SYSTEM vs imported)
-- 3. Import-job tracking table
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add street column to hand_actions
--    Nullable: existing rows (from before this migration) will be NULL.
ALTER TABLE hand_actions
  ADD COLUMN IF NOT EXISTS street VARCHAR(20);

-- 2. Tag hands by origin
ALTER TABLE hands
  ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE hands
  ADD CONSTRAINT chk_hands_source
  CHECK (source IN ('SYSTEM','POKERSTARS','GGPOKER'));

-- 3. Import job tracking
CREATE TABLE IF NOT EXISTS hand_imports (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id       UUID         NOT NULL REFERENCES players(id),
    filename        VARCHAR(255) NOT NULL,
    source          VARCHAR(20)  NOT NULL CHECK (source IN ('POKERSTARS','GGPOKER')),
    hands_parsed    INT          NOT NULL DEFAULT 0,
    hands_imported  INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                 CHECK (status IN ('PENDING','PROCESSING','DONE','FAILED')),
    error_message   TEXT,
    imported_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hand_imports_player ON hand_imports(player_id);
