ALTER TABLE public.players
    DROP CONSTRAINT IF EXISTS chk_players_highest_achieved_tier;

ALTER TABLE public.players
    ADD CONSTRAINT chk_players_highest_achieved_tier
    CHECK (
        highest_achieved_tier IS NULL
        OR highest_achieved_tier IN (
            'D', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S-', 'S', 'S+'
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
            'D', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S-', 'S', 'S+'
        )
    ) NOT VALID;

ALTER TABLE public.players
    VALIDATE CONSTRAINT chk_players_dormancy_episode_floor_tier;
