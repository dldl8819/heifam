CREATE TABLE IF NOT EXISTS user_race_preferences (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    preferred_race VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_race_preferences_preferred_race
        CHECK (preferred_race IN ('P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'R'))
);
