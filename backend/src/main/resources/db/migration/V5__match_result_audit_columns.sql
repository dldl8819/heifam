ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS result_recorded_at TIMESTAMPTZ;

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS result_recorded_by_email VARCHAR(320);
