SET LOCAL lock_timeout = '5s';

ALTER TABLE public.players
    ADD COLUMN IF NOT EXISTS highest_achieved_tier VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_players_highest_achieved_tier'
          AND conrelid = 'public.players'::regclass
    ) THEN
        ALTER TABLE public.players
            ADD CONSTRAINT chk_players_highest_achieved_tier
            CHECK (
                highest_achieved_tier IS NULL
                OR highest_achieved_tier IN (
                    'D', 'C-', 'C', 'C+', 'B-', 'B', 'B+', 'A-', 'A', 'A+', 'S'
                )
            ) NOT VALID;
    END IF;
END
$$;
