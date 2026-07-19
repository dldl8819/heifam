package com.balancify.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountPersonalDataRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccountPersonalDataRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    public Optional<String> findAccountEmail(UUID authUserId) {
        if (authUserId == null) {
            return Optional.empty();
        }
        List<String> emails = jdbcTemplate.query(
            """
            SELECT LOWER(BTRIM(email)) AS normalized_email
            FROM public.users
            WHERE id = :authUserId
              AND NULLIF(BTRIM(email), '') IS NOT NULL
            """,
            Map.of("authUserId", authUserId),
            (resultSet, rowNumber) -> resultSet.getString("normalized_email")
        );
        return emails.stream().findFirst().map(String::trim).filter(value -> !value.isEmpty());
    }


    public Optional<String> findLinkedNickname(String normalizedEmail) {
        List<String> nicknames = jdbcTemplate.query(
            """
            WITH account_profiles AS (
                SELECT normalized_email, nickname, 1 AS priority
                FROM managed_admin_emails
                UNION ALL
                SELECT normalized_email, nickname, 2 AS priority
                FROM allowed_user_emails
            ),
            selected_profile AS (
                SELECT normalized_email, nickname
                FROM account_profiles
                WHERE normalized_email = :email
                  AND NULLIF(BTRIM(nickname), '') IS NOT NULL
                ORDER BY priority
                LIMIT 1
            )
            SELECT selected_profile.nickname
            FROM selected_profile
            WHERE NOT EXISTS (
                SELECT 1
                FROM account_profiles other_profile
                WHERE LOWER(BTRIM(other_profile.normalized_email)) <> :email
                  AND NULLIF(BTRIM(other_profile.nickname), '') IS NOT NULL
                  AND LOWER(BTRIM(other_profile.nickname)) = LOWER(BTRIM(selected_profile.nickname))
            )
            """,
            Map.of("email", normalizedEmail),
            (resultSet, rowNumber) -> resultSet.getString("nickname")
        );
        return nicknames.stream().findFirst().map(String::trim).filter(value -> !value.isEmpty());
    }

    public void anonymizeHistoricalIdentity(
        String normalizedEmail,
        List<Long> playerIds,
        String deletedMemberLabel
    ) {
        MapSqlParameterSource identityParameters = new MapSqlParameterSource()
            .addValue("email", normalizedEmail)
            .addValue("deletedMemberLabel", deletedMemberLabel);

        jdbcTemplate.update(
            """
            UPDATE matches
            SET result_recorded_by_email = NULL,
                result_recorded_by_nickname = :deletedMemberLabel
            WHERE LOWER(BTRIM(result_recorded_by_email)) = :email
            """,
            identityParameters
        );
        jdbcTemplate.update(
            """
            UPDATE operation_audit_logs
            SET actor_email = NULL,
                actor_nickname = :deletedMemberLabel
            WHERE LOWER(BTRIM(actor_email)) = :email
            """,
            identityParameters
        );

        anonymizeHistoricalPlayerIdentity(playerIds, deletedMemberLabel);
    }

    public void anonymizeHistoricalIdentityInGroup(
        String normalizedEmail,
        Long groupId,
        List<Long> playerIds,
        String deletedMemberLabel
    ) {
        if (normalizedEmail != null && !normalizedEmail.isBlank() && groupId != null) {
            MapSqlParameterSource identityParameters = new MapSqlParameterSource()
                .addValue("email", normalizedEmail)
                .addValue("groupId", groupId)
                .addValue("deletedMemberLabel", deletedMemberLabel);
            jdbcTemplate.update(
                """
                UPDATE matches
                SET result_recorded_by_email = NULL,
                    result_recorded_by_nickname = :deletedMemberLabel
                WHERE group_id = :groupId
                  AND LOWER(BTRIM(result_recorded_by_email)) = :email
                """,
                identityParameters
            );
            jdbcTemplate.update(
                """
                UPDATE operation_audit_logs
                SET actor_email = NULL,
                    actor_nickname = :deletedMemberLabel
                WHERE group_id = :groupId
                  AND LOWER(BTRIM(actor_email)) = :email
                """,
                identityParameters
            );
        }
        anonymizeHistoricalPlayerIdentity(playerIds, deletedMemberLabel);
    }

    public void anonymizeHistoricalPlayerIdentity(
        List<Long> playerIds,
        String deletedMemberLabel
    ) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
            """
            UPDATE operation_audit_logs
            SET target_id = NULL,
                target_label = :deletedMemberLabel,
                details = NULL
            WHERE target_type = 'PLAYER'
              AND target_id IN (:playerIds)
            """,
            new MapSqlParameterSource()
                .addValue("deletedMemberLabel", deletedMemberLabel)
                .addValue("playerIds", playerIds)
        );
    }

    public void deleteAccountIdentity(UUID authUserId, String normalizedEmail) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("authUserId", authUserId);

        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            parameters.addValue("email", normalizedEmail);
            for (String table : List.of(
                "managed_admin_emails",
                "allowed_user_emails",
                "admin_mmr_access_emails"
            )) {
                jdbcTemplate.update(
                    "UPDATE " + table + " SET created_by_email = NULL "
                        + "WHERE LOWER(BTRIM(created_by_email)) = :email",
                    parameters
                );
            }
            jdbcTemplate.update(
                "DELETE FROM admin_mmr_access_emails WHERE normalized_email = :email",
                parameters
            );
            jdbcTemplate.update(
                "DELETE FROM managed_admin_emails WHERE normalized_email = :email",
                parameters
            );
            jdbcTemplate.update(
                "DELETE FROM allowed_user_emails WHERE normalized_email = :email",
                parameters
            );
            jdbcTemplate.update(
                "DELETE FROM user_race_preferences WHERE normalized_email = :email",
                parameters
            );
        }
        if (authUserId != null) {
            jdbcTemplate.update("DELETE FROM public.users WHERE id = :authUserId", parameters);
        }
    }

    public void enqueuePendingAuthDeletion(UUID authUserId, OffsetDateTime createdAt) {
        if (authUserId == null) {
            return;
        }
        jdbcTemplate.update(
            """
            INSERT INTO pending_auth_user_deletions (auth_user_id, created_at, last_attempt_at)
            VALUES (:authUserId, :createdAt, NULL)
            ON CONFLICT (auth_user_id) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("authUserId", authUserId)
                .addValue("createdAt", createdAt)
        );
    }

    public List<UUID> claimPendingAuthDeletions(
        OffsetDateTime claimedAt,
        OffsetDateTime retryBefore,
        int batchSize
    ) {
        return jdbcTemplate.query(
            """
            WITH candidates AS (
                SELECT auth_user_id
                FROM pending_auth_user_deletions
                WHERE last_attempt_at IS NULL OR last_attempt_at < :retryBefore
                ORDER BY created_at, auth_user_id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE pending_auth_user_deletions pending
            SET last_attempt_at = :claimedAt
            FROM candidates
            WHERE pending.auth_user_id = candidates.auth_user_id
            RETURNING pending.auth_user_id
            """,
            new MapSqlParameterSource()
                .addValue("claimedAt", claimedAt)
                .addValue("retryBefore", retryBefore)
                .addValue("batchSize", Math.max(1, batchSize)),
            (resultSet, rowNumber) -> resultSet.getObject("auth_user_id", UUID.class)
        );
    }

    public void deletePendingAuthDeletion(UUID authUserId) {
        if (authUserId == null) {
            return;
        }
        jdbcTemplate.update(
            "DELETE FROM pending_auth_user_deletions WHERE auth_user_id = :authUserId",
            Map.of("authUserId", authUserId)
        );
    }
}
