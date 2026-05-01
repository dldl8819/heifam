ALTER TABLE players
    ADD COLUMN IF NOT EXISTS last_tier_snapshot_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_tier_snapshot_mmr INTEGER;
