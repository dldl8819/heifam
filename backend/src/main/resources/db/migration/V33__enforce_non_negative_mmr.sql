DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_players_mmr_non_negative'
    ) THEN
        ALTER TABLE players
            ADD CONSTRAINT chk_players_mmr_non_negative
            CHECK (mmr >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_players_base_mmr_non_negative'
    ) THEN
        ALTER TABLE players
            ADD CONSTRAINT chk_players_base_mmr_non_negative
            CHECK (base_mmr IS NULL OR base_mmr >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_players_last_tier_snapshot_mmr_non_negative'
    ) THEN
        ALTER TABLE players
            ADD CONSTRAINT chk_players_last_tier_snapshot_mmr_non_negative
            CHECK (last_tier_snapshot_mmr IS NULL OR last_tier_snapshot_mmr >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_match_participants_mmr_before_non_negative'
    ) THEN
        ALTER TABLE match_participants
            ADD CONSTRAINT chk_match_participants_mmr_before_non_negative
            CHECK (mmr_before IS NULL OR mmr_before >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_match_participants_mmr_after_non_negative'
    ) THEN
        ALTER TABLE match_participants
            ADD CONSTRAINT chk_match_participants_mmr_after_non_negative
            CHECK (mmr_after IS NULL OR mmr_after >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_mmr_history_before_mmr_non_negative'
    ) THEN
        ALTER TABLE mmr_history
            ADD CONSTRAINT chk_mmr_history_before_mmr_non_negative
            CHECK (before_mmr IS NULL OR before_mmr >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_mmr_history_after_mmr_non_negative'
    ) THEN
        ALTER TABLE mmr_history
            ADD CONSTRAINT chk_mmr_history_after_mmr_non_negative
            CHECK (after_mmr IS NULL OR after_mmr >= 0);
    END IF;
END $$;
