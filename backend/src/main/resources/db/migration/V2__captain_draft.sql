CREATE TABLE IF NOT EXISTS captain_drafts (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFTING',
    sets_per_round INTEGER NOT NULL DEFAULT 4,
    current_turn_team VARCHAR(20),
    home_captain_player_id BIGINT NOT NULL,
    away_captain_player_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_captain_drafts_group_id
    ON captain_drafts (group_id);

CREATE TABLE IF NOT EXISTS captain_draft_participants (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    team VARCHAR(20),
    captain BOOLEAN NOT NULL DEFAULT FALSE,
    pick_order INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_captain_draft_participants_draft_player
        UNIQUE (draft_id, player_id)
);

CREATE INDEX IF NOT EXISTS idx_captain_draft_participants_draft_id
    ON captain_draft_participants (draft_id);

CREATE INDEX IF NOT EXISTS idx_captain_draft_participants_player_id
    ON captain_draft_participants (player_id);

CREATE TABLE IF NOT EXISTS captain_draft_entries (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    round_number INTEGER NOT NULL,
    round_code VARCHAR(20) NOT NULL,
    set_number INTEGER NOT NULL,
    home_player_id BIGINT,
    away_player_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_captain_draft_entries_draft_round_set
        UNIQUE (draft_id, round_number, set_number)
);

CREATE INDEX IF NOT EXISTS idx_captain_draft_entries_draft_id
    ON captain_draft_entries (draft_id);
