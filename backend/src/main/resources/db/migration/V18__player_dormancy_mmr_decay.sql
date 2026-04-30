ALTER TABLE players
    ADD COLUMN IF NOT EXISTS last_dormancy_mmr_decay_at TIMESTAMPTZ;
