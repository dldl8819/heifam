ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS result_recorded_by_nickname VARCHAR(100);
