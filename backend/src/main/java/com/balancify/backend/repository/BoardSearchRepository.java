package com.balancify.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Finds posts by a word in their title or text, across the notices and the free board, newest
 * first. The anonymous board is never searched, for anyone: what is written there is for the
 * admins' eyes on its own page, not something to turn up beside a name in a list of results.
 */
@Repository
public class BoardSearchRepository {

    public static final String KIND_NOTICE = "NOTICE";
    public static final String KIND_FREE = "FREE";

    public record FoundRow(
        String kind,
        Long id,
        String title,
        String content,
        String authorEmail,
        OffsetDateTime createdAt,
        boolean adminOnly,
        String voteStatus,
        long viewCount,
        long commentCount,
        long likeCount,
        long voteCount
    ) {
    }

    // A notice's text without the places of its images: "[[image:12]]" is not something anyone wrote.
    private static final String NOTICE_TEXT = "regexp_replace(n.content, '\\[\\[image:[0-9]+\\]\\]', '', 'g')";

    private static final String NOTICE_MATCH =
        "n.group_id = :groupId AND (CAST(:admin AS boolean) OR n.admin_only = false) "
            + "AND (n.title ILIKE :pattern ESCAPE '\\' OR " + NOTICE_TEXT + " ILIKE :pattern ESCAPE '\\') ";

    private static final String FREE_POST_MATCH =
        "p.group_id = :groupId AND p.board = 'FREE' "
            + "AND (p.title ILIKE :pattern ESCAPE '\\' OR p.content ILIKE :pattern ESCAPE '\\') ";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BoardSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * pattern is a LIKE pattern whose own wildcards are escaped with a backslash. Notices kept to
     * admins are found only with admin true.
     */
    public List<FoundRow> search(Long groupId, String pattern, boolean admin, int limit, int offset) {
        return jdbcTemplate.query(
            "SELECT * FROM ("
                + "SELECT '" + KIND_NOTICE + "' AS kind, n.id, n.title, " + NOTICE_TEXT + " AS content, "
                + "n.author_email, n.created_at, n.admin_only, n.vote_status, "
                + "(SELECT count(*) FROM notice_reads r WHERE r.notice_id = n.id) AS view_count, "
                + "(SELECT count(*) FROM notice_comments c WHERE c.notice_id = n.id AND c.deleted_at IS NULL) AS comment_count, "
                + "(SELECT count(*) FROM notice_likes l WHERE l.notice_id = n.id) AS like_count, "
                + "(SELECT count(*) FROM notice_votes v WHERE v.notice_id = n.id) AS vote_count "
                + "FROM notices n WHERE " + NOTICE_MATCH
                + "UNION ALL "
                + "SELECT '" + KIND_FREE + "' AS kind, p.id, p.title, p.content, "
                + "p.author_email, p.created_at, false AS admin_only, 'NONE' AS vote_status, "
                + "(SELECT count(*) FROM board_post_views v WHERE v.post_id = p.id) AS view_count, "
                + "(SELECT count(*) FROM board_comments c WHERE c.post_id = p.id) AS comment_count, "
                + "(SELECT count(*) FROM board_post_likes l WHERE l.post_id = p.id) AS like_count, "
                + "0 AS vote_count "
                + "FROM board_posts p WHERE " + FREE_POST_MATCH
                + ") found ORDER BY created_at DESC, kind DESC, id DESC LIMIT :limit OFFSET :offset",
            scope(groupId, pattern, admin).addValue("limit", limit).addValue("offset", offset),
            (row, rowNumber) -> found(row)
        );
    }

    public long count(Long groupId, String pattern, boolean admin) {
        Long found = jdbcTemplate.queryForObject(
            "SELECT (SELECT count(*) FROM notices n WHERE " + NOTICE_MATCH + ") "
                + "+ (SELECT count(*) FROM board_posts p WHERE " + FREE_POST_MATCH + ")",
            scope(groupId, pattern, admin),
            Long.class
        );
        return found == null ? 0 : found;
    }

    private MapSqlParameterSource scope(Long groupId, String pattern, boolean admin) {
        return new MapSqlParameterSource()
            .addValue("groupId", groupId)
            .addValue("pattern", pattern)
            .addValue("admin", admin);
    }

    private static FoundRow found(ResultSet row) throws SQLException {
        return new FoundRow(
            row.getString("kind"),
            row.getLong("id"),
            row.getString("title"),
            row.getString("content"),
            row.getString("author_email"),
            row.getObject("created_at", OffsetDateTime.class),
            row.getBoolean("admin_only"),
            row.getString("vote_status"),
            row.getLong("view_count"),
            row.getLong("comment_count"),
            row.getLong("like_count"),
            row.getLong("vote_count")
        );
    }
}
