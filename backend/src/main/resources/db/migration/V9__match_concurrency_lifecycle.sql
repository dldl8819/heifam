ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE matches
SET status = 'COMPLETED'
WHERE status IS NULL
  AND winning_team IS NOT NULL
  AND BTRIM(winning_team) <> '';

UPDATE matches
SET status = 'CONFIRMED'
WHERE status IS NULL;

ALTER TABLE matches
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS team_size INTEGER;

UPDATE matches
SET team_size = COALESCE(team_size, 3);

ALTER TABLE matches
    ALTER COLUMN team_size SET NOT NULL;

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE matches
SET version = COALESCE(version, 0);

ALTER TABLE matches
    ALTER COLUMN version SET NOT NULL;

ALTER TABLE matches
    ALTER COLUMN version SET DEFAULT 0;

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS participant_signature VARCHAR(255);

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS team_signature VARCHAR(255);

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

UPDATE matches
SET created_at = COALESCE(created_at, played_at, NOW());

ALTER TABLE matches
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE matches
    ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE matches
SET updated_at = COALESCE(updated_at, result_recorded_at, played_at, NOW());

ALTER TABLE matches
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE matches
    ALTER COLUMN updated_at SET DEFAULT NOW();

ALTER TABLE match_participants
    ADD COLUMN IF NOT EXISTS slot INTEGER;

ALTER TABLE match_participants
    ADD COLUMN IF NOT EXISTS result VARCHAR(20);

ALTER TABLE mmr_history
    ADD COLUMN IF NOT EXISTS delta INTEGER;

UPDATE mmr_history
SET delta = COALESCE(after_mmr, 0) - COALESCE(before_mmr, 0)
WHERE delta IS NULL;

ALTER TABLE mmr_history
    ALTER COLUMN delta SET NOT NULL;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_match_participants_match_player'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM match_participants
        GROUP BY match_id, player_id
        HAVING COUNT(*) > 1
    ) THEN
        ALTER TABLE match_participants
            ADD CONSTRAINT uq_match_participants_match_player UNIQUE (match_id, player_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_matches_group_status_played_at
    ON matches (group_id, status, played_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_matches_group_played_at
    ON matches (group_id, played_at DESC, id DESC);
