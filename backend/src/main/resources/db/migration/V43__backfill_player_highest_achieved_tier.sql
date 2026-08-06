WITH tier_rank(tier, rank) AS (
    VALUES
        ('D', 1),
        ('C-', 2),
        ('C', 3),
        ('C+', 4),
        ('B-', 5),
        ('B', 6),
        ('B+', 7),
        ('A-', 8),
        ('A', 9),
        ('A+', 10),
        ('S', 11)
),
player_tier_evidence AS (
    SELECT
        player.id,
        evidence.tier
    FROM public.players AS player
    CROSS JOIN LATERAL (
        VALUES
            (UPPER(TRIM(COALESCE(player.highest_achieved_tier, '')))),
            (UPPER(TRIM(COALESCE(player.tier, '')))),
            (UPPER(TRIM(COALESCE(player.tier_change_acknowledged_tier, '')))),
            (CASE
                WHEN player.base_mmr >= 2000 THEN 'S'
                WHEN player.base_mmr >= 1800 THEN 'A+'
                WHEN player.base_mmr >= 1600 THEN 'A'
                WHEN player.base_mmr >= 1400 THEN 'A-'
                WHEN player.base_mmr >= 1200 THEN 'B+'
                WHEN player.base_mmr >= 1000 THEN 'B'
                WHEN player.base_mmr >= 800 THEN 'B-'
                WHEN player.base_mmr >= 600 THEN 'C+'
                WHEN player.base_mmr >= 400 THEN 'C'
                WHEN player.base_mmr >= 200 THEN 'C-'
                WHEN player.base_mmr > 0 THEN 'D'
                ELSE ''
            END),
            (CASE
                WHEN player.last_tier_snapshot_mmr >= 2000 THEN 'S'
                WHEN player.last_tier_snapshot_mmr >= 1800 THEN 'A+'
                WHEN player.last_tier_snapshot_mmr >= 1600 THEN 'A'
                WHEN player.last_tier_snapshot_mmr >= 1400 THEN 'A-'
                WHEN player.last_tier_snapshot_mmr >= 1200 THEN 'B+'
                WHEN player.last_tier_snapshot_mmr >= 1000 THEN 'B'
                WHEN player.last_tier_snapshot_mmr >= 800 THEN 'B-'
                WHEN player.last_tier_snapshot_mmr >= 600 THEN 'C+'
                WHEN player.last_tier_snapshot_mmr >= 400 THEN 'C'
                WHEN player.last_tier_snapshot_mmr >= 200 THEN 'C-'
                WHEN player.last_tier_snapshot_mmr > 0 THEN 'D'
                ELSE ''
            END),
            (CASE
                -- V41 also stored a live-MMR floor in some rows. Only invert floors
                -- known to have come from the stored-tier two-step branch.
                WHEN player.last_tier_recalculated_at IS NULL
                  OR player.last_tier_recalculated_at < player.dormant_since
                THEN CASE UPPER(TRIM(COALESCE(player.dormancy_episode_floor_tier, '')))
                    WHEN 'A' THEN 'S'
                    WHEN 'A-' THEN 'A+'
                    WHEN 'B+' THEN 'A'
                    WHEN 'B' THEN 'A-'
                    WHEN 'B-' THEN 'B+'
                    WHEN 'C+' THEN 'B'
                    WHEN 'C' THEN 'B-'
                    WHEN 'C-' THEN 'C+'
                    WHEN 'D' THEN 'D'
                    ELSE ''
                END
                ELSE ''
            END)
    ) AS evidence(tier)
),
highest_tier_by_player AS (
    SELECT
        evidence.id,
        MAX(tier_rank.rank) AS highest_rank
    FROM player_tier_evidence AS evidence
    JOIN tier_rank ON tier_rank.tier = evidence.tier
    GROUP BY evidence.id
),
resolved_highest_tier AS (
    SELECT
        highest.id,
        tier_rank.tier
    FROM highest_tier_by_player AS highest
    JOIN tier_rank ON tier_rank.rank = highest.highest_rank
)
UPDATE public.players AS player
SET highest_achieved_tier = resolved.tier
FROM resolved_highest_tier AS resolved
WHERE player.id = resolved.id
  AND player.highest_achieved_tier IS DISTINCT FROM resolved.tier;

WITH tier_rank(tier, rank) AS (
    VALUES
        ('D', 1),
        ('C-', 2),
        ('C', 3),
        ('C+', 4),
        ('B-', 5),
        ('B', 6),
        ('B+', 7),
        ('A-', 8),
        ('A', 9),
        ('A+', 10),
        ('S', 11)
),
required_dormancy_floor AS (
    SELECT
        player.id,
        floor_tier.tier,
        floor_tier.rank,
        existing_floor.rank AS existing_rank
    FROM public.players AS player
    JOIN tier_rank AS highest_tier
      ON highest_tier.tier = player.highest_achieved_tier
    JOIN tier_rank AS floor_tier
      ON floor_tier.rank = GREATEST(1, highest_tier.rank - 2)
    LEFT JOIN tier_rank AS existing_floor
      ON existing_floor.tier = player.dormancy_episode_floor_tier
    WHERE player.active = TRUE
      AND player.anonymized_at IS NULL
      AND player.dormant_since IS NOT NULL
      AND player.returned_at IS NULL
)
UPDATE public.players AS player
SET dormancy_episode_floor_tier = required.tier
FROM required_dormancy_floor AS required
WHERE player.id = required.id
  AND (required.existing_rank IS NULL OR required.existing_rank < required.rank);
