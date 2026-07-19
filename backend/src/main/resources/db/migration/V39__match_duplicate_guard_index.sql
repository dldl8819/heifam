CREATE INDEX IF NOT EXISTS idx_matches_duplicate_guard_lookup
    ON matches (group_id, team_size, participant_signature, race_composition, created_at DESC)
    WHERE status <> 'CANCELLED';
