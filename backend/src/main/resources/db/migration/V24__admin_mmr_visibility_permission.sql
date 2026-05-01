CREATE TABLE IF NOT EXISTS admin_mmr_access_emails (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    created_by_email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
