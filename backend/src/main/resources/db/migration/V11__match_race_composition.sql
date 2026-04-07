ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS race_composition VARCHAR(10);
