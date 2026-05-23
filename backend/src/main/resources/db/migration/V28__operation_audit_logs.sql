CREATE TABLE IF NOT EXISTS operation_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(60) NOT NULL,
    actor_email VARCHAR(320),
    actor_nickname VARCHAR(100),
    target_type VARCHAR(60) NOT NULL,
    target_id BIGINT,
    target_label VARCHAR(255),
    group_id BIGINT,
    summary VARCHAR(500) NOT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_operation_audit_logs_created_at
    ON operation_audit_logs (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_operation_audit_logs_action
    ON operation_audit_logs (action);
