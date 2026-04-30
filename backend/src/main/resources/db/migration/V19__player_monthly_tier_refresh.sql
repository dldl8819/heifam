ALTER TABLE players
    ADD COLUMN IF NOT EXISTS last_tier_recalculated_at TIMESTAMPTZ;
