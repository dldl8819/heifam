ALTER TABLE match_participants
ADD COLUMN IF NOT EXISTS race VARCHAR(2);

UPDATE match_participants mp
SET race = COALESCE(NULLIF(UPPER(BTRIM(p.race)), ''), 'P')
FROM players p
WHERE mp.player_id = p.id
  AND (mp.race IS NULL OR BTRIM(mp.race) = '');

UPDATE match_participants
SET race = 'P'
WHERE race IS NULL OR BTRIM(race) = '';

ALTER TABLE match_participants
ALTER COLUMN race SET NOT NULL;
