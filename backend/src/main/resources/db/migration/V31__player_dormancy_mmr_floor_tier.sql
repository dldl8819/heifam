ALTER TABLE players
    ADD COLUMN IF NOT EXISTS dormancy_mmr_floor_tier VARCHAR(20);
