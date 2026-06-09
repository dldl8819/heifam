WITH actor_names AS (
    SELECT normalized_email, nickname, 1 AS priority
    FROM managed_admin_emails
    WHERE NULLIF(BTRIM(nickname), '') IS NOT NULL
    UNION ALL
    SELECT normalized_email, nickname, 2 AS priority
    FROM allowed_user_emails
    WHERE NULLIF(BTRIM(nickname), '') IS NOT NULL
),
preferred_actor_names AS (
    SELECT DISTINCT ON (normalized_email)
        normalized_email,
        LEFT(BTRIM(nickname), 100) AS nickname
    FROM actor_names
    ORDER BY normalized_email, priority
)
UPDATE operation_audit_logs AS logs
SET actor_nickname = names.nickname
FROM preferred_actor_names AS names
WHERE NULLIF(BTRIM(logs.actor_nickname), '') IS NULL
  AND LOWER(BTRIM(logs.actor_email)) = names.normalized_email;
