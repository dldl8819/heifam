ALTER TABLE managed_admin_emails
    ADD COLUMN IF NOT EXISTS nickname VARCHAR(100);

ALTER TABLE allowed_user_emails
    ADD COLUMN IF NOT EXISTS nickname VARCHAR(100);
