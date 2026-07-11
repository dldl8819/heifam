package com.balancify.backend.repository;

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

        if (playerIds != null && !playerIds.isEmpty()) {
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
    }

    public void deleteAccountIdentity(UUID authUserId, String normalizedEmail) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("authUserId", authUserId)
            .addValue("email", normalizedEmail);

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
        jdbcTemplate.update(
            """
            DELETE FROM public.users
            WHERE id = :authUserId
               OR LOWER(BTRIM(email)) = :email
            """,
            parameters
        );
    }
}
