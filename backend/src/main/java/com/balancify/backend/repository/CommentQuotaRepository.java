package com.balancify.backend.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The comments one person wrote, on notices and on every board together, for the daily comment
 * limit. A person is :email with the emails grouped with it (access_people).
 */
@Repository
public class CommentQuotaRepository {

    // One key a person: the group the email belongs to, or the email itself when it is not grouped.
    static final String LOCK_COMMENTER =
        "SELECT pg_advisory_xact_lock(hashtextextended('comment-quota:' || COALESCE("
            + "(SELECT 'person:' || person_id FROM access_person_emails WHERE normalized_email = :email), "
            + "'email:' || :email), 0))";

    // A removed comment is gone from the boards, and on a notice keeps its place without its writer.
    static final String COUNT_SINCE =
        "SELECT (SELECT count(*) FROM notice_comments WHERE " + PersonSql.samePerson("author_email")
            + " AND created_at >= :since) + (SELECT count(*) FROM board_comments WHERE "
            + PersonSql.samePerson("author_email") + " AND created_at >= :since)";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CommentQuotaRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Holds the person :email is until the transaction ends, so that two comments of theirs, from
     * one email or from two, are counted one after the other. Unlike PersonSql.LOCK_PERSON, this
     * also holds an email that is not grouped.
     */
    public void lockCommenter(String email) {
        jdbcTemplate.queryForList(LOCK_COMMENTER, new MapSqlParameterSource("email", email));
    }

    /** Comments of the person :email is, written since :since and not removed, replies included. */
    public long countCommentsSince(String email, OffsetDateTime since) {
        Long found = jdbcTemplate.queryForObject(
            COUNT_SINCE,
            new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("since", since, Types.TIMESTAMP_WITH_TIMEZONE),
            Long.class
        );
        return found == null ? 0 : found;
    }
}
