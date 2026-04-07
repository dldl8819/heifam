UPDATE players
SET race = 'PTZ'
WHERE UPPER(TRIM(race)) = 'R';

UPDATE match_participants
SET race = 'PTZ'
WHERE UPPER(TRIM(race)) = 'R';

UPDATE user_race_preferences
SET preferred_race = 'PTZ'
WHERE UPPER(TRIM(preferred_race)) = 'R';

ALTER TABLE user_race_preferences
    DROP CONSTRAINT IF EXISTS chk_user_race_preferences_preferred_race;

ALTER TABLE user_race_preferences
    ADD CONSTRAINT chk_user_race_preferences_preferred_race
        CHECK (preferred_race IN ('P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'PTZ'));
