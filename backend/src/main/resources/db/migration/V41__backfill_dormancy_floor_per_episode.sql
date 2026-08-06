WITH current_dormant_players AS (
    SELECT
        id,
        CASE
            WHEN (
                last_tier_recalculated_at IS NULL
                OR last_tier_recalculated_at < dormant_since
            )
            AND UPPER(TRIM(COALESCE(tier, ''))) IN (
                'S', 'A+', 'A', 'A-', 'B+', 'B', 'B-', 'C+', 'C', 'C-', 'D'
            )
            THEN CASE UPPER(TRIM(tier))
                WHEN 'S' THEN 'A'
                WHEN 'A+' THEN 'A-'
                WHEN 'A' THEN 'B+'
                WHEN 'A-' THEN 'B'
                WHEN 'B+' THEN 'B-'
                WHEN 'B' THEN 'C+'
                WHEN 'B-' THEN 'C'
                WHEN 'C+' THEN 'C-'
                WHEN 'C' THEN 'D'
                WHEN 'C-' THEN 'D'
                WHEN 'D' THEN 'D'
                ELSE NULL
            END
            WHEN mmr >= 2000 THEN 'S'
            WHEN mmr >= 1800 THEN 'A+'
            WHEN mmr >= 1600 THEN 'A'
            WHEN mmr >= 1400 THEN 'A-'
            WHEN mmr >= 1200 THEN 'B+'
            WHEN mmr >= 1000 THEN 'B'
            WHEN mmr >= 800 THEN 'B-'
            WHEN mmr >= 600 THEN 'C+'
            WHEN mmr >= 400 THEN 'C'
            WHEN mmr >= 200 THEN 'C-'
            WHEN mmr > 0 THEN 'D'
            ELSE NULL
        END AS floor_tier
    FROM public.players
    WHERE active = TRUE
      AND anonymized_at IS NULL
      AND dormant_since IS NOT NULL
      AND returned_at IS NULL
      AND dormancy_episode_floor_tier IS NULL
)
UPDATE public.players AS player
SET dormancy_episode_floor_tier = current_dormant_players.floor_tier
FROM current_dormant_players
WHERE player.id = current_dormant_players.id
  AND current_dormant_players.floor_tier IS NOT NULL;

ALTER TABLE public.players
    VALIDATE CONSTRAINT chk_players_dormancy_episode_floor_tier;
