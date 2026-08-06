ALTER TABLE public.players
    ADD COLUMN IF NOT EXISTS dormancy_episode_floor_tier VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_players_dormancy_episode_floor_tier'
          AND conrelid = 'public.players'::regclass
    ) THEN
        ALTER TABLE public.players
            ADD CONSTRAINT chk_players_dormancy_episode_floor_tier
            CHECK (
                dormancy_episode_floor_tier IS NULL
                OR dormancy_episode_floor_tier IN (
                    'D', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S'
                )
            ) NOT VALID;
    END IF;
END
$$;
