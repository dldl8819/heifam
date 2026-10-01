-- MMR may now go below zero, so the non-negative checks from V33 go.
ALTER TABLE public.players DROP CONSTRAINT IF EXISTS chk_players_mmr_non_negative;
ALTER TABLE public.players DROP CONSTRAINT IF EXISTS chk_players_base_mmr_non_negative;
ALTER TABLE public.players DROP CONSTRAINT IF EXISTS chk_players_last_tier_snapshot_mmr_non_negative;
ALTER TABLE public.match_participants DROP CONSTRAINT IF EXISTS chk_match_participants_mmr_before_non_negative;
ALTER TABLE public.match_participants DROP CONSTRAINT IF EXISTS chk_match_participants_mmr_after_non_negative;
ALTER TABLE public.mmr_history DROP CONSTRAINT IF EXISTS chk_mmr_history_before_mmr_non_negative;
ALTER TABLE public.mmr_history DROP CONSTRAINT IF EXISTS chk_mmr_history_after_mmr_non_negative;

-- D splits into D- (-201 and below), D (-200 to -1) and D+ (0 to 199).
ALTER TABLE public.players
    DROP CONSTRAINT IF EXISTS chk_players_highest_achieved_tier;

ALTER TABLE public.players
    ADD CONSTRAINT chk_players_highest_achieved_tier
    CHECK (
        highest_achieved_tier IS NULL
        OR highest_achieved_tier IN (
            'D-', 'D', 'D+', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S-', 'S', 'S+'
        )
    ) NOT VALID;

ALTER TABLE public.players
    VALIDATE CONSTRAINT chk_players_highest_achieved_tier;

ALTER TABLE public.players
    DROP CONSTRAINT IF EXISTS chk_players_dormancy_episode_floor_tier;

ALTER TABLE public.players
    ADD CONSTRAINT chk_players_dormancy_episode_floor_tier
    CHECK (
        dormancy_episode_floor_tier IS NULL
        OR dormancy_episode_floor_tier IN (
            'D-', 'D', 'D+', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S-', 'S', 'S+'
        )
    ) NOT VALID;

ALTER TABLE public.players
    VALIDATE CONSTRAINT chk_players_dormancy_episode_floor_tier;

-- The old D covered 1 to 199, which is D+ now.
UPDATE public.players SET tier = 'D+' WHERE upper(btrim(tier)) = 'D';
UPDATE public.players SET highest_achieved_tier = 'D+' WHERE highest_achieved_tier = 'D';

-- A stored value that is not a tier used to show the player's score tier, or 미배정 at MMR 0.
-- An unassigned stored tier now always means 미배정, so store what they show today.
UPDATE public.players
SET tier = CASE
    WHEN mmr IS NULL OR mmr = 0 THEN 'UNASSIGNED'
    WHEN mmr < -200 THEN 'D-'
    WHEN mmr < 0 THEN 'D'
    WHEN mmr < 200 THEN 'D+'
    WHEN mmr < 400 THEN 'C-'
    WHEN mmr < 600 THEN 'C'
    WHEN mmr < 800 THEN 'C+'
    WHEN mmr < 1000 THEN 'B-'
    WHEN mmr < 1200 THEN 'B'
    WHEN mmr < 1400 THEN 'B+'
    WHEN mmr < 1600 THEN 'A-'
    WHEN mmr < 1800 THEN 'A'
    WHEN mmr < 2000 THEN 'A+'
    WHEN mmr < 2200 THEN 'S-'
    WHEN mmr < 2400 THEN 'S'
    ELSE 'S+'
END
WHERE tier IS NULL
    OR upper(btrim(tier)) NOT IN (
        'D-', 'D', 'D+', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S-', 'S', 'S+'
    );
