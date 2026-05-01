ALTER TABLE players
    ADD COLUMN IF NOT EXISTS tier_change_acknowledged_tier VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tier_change_acknowledged_at TIMESTAMPTZ;
