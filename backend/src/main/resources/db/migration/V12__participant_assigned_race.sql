ALTER TABLE players
    ALTER COLUMN race TYPE VARCHAR(3);

ALTER TABLE match_participants
    ALTER COLUMN race TYPE VARCHAR(3);

ALTER TABLE match_participants
    ADD COLUMN IF NOT EXISTS assigned_race VARCHAR(1);
