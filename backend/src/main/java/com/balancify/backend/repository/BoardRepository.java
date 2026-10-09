package com.balancify.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Posts, comments, likes and views of the member boards (the free board and the anonymous board).
 * Every row keeps the email of who made it; what of that may be shown is BoardService's to decide.
 * A like and a view are one row per person and post: asking twice changes nothing.
 */
@Repository
public class BoardRepository {

    public record PostRow(
        Long id,
        String board,
        String title,
        String content,
        String authorEmail,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long commentCount,
        long likeCount,
        long viewCount
    ) {
    }

    public record CommentRow(Long id, Long postId, String authorEmail, String content, OffsetDateTime createdAt) {
    }

    private static final String POST_COLUMNS =
        "p.id, p.board, p.title, p.content, p.author_email, p.created_at, p.updated_at, "
            + "(SELECT count(*) FROM board_comments c WHERE c.post_id = p.id) AS comment_count, "
            + "(SELECT count(*) FROM board_post_likes l WHERE l.post_id = p.id) AS like_count, "
            + "(SELECT count(*) FROM board_post_views v WHERE v.post_id = p.id) AS view_count ";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BoardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertPost(Long groupId, String board, String title, String content, String authorEmail) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO board_posts (group_id, board, title, content, author_email) "
                + "VALUES (:groupId, :board, :title, :content, :authorEmail)",
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("board", board)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("authorEmail", authorEmail),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Board post id was not returned");
        }
        return id.longValue();
    }

    public Optional<PostRow> findPost(Long groupId, String board, Long postId) {
        return jdbcTemplate.query(
            "SELECT " + POST_COLUMNS + "FROM board_posts p "
                + "WHERE p.id = :postId AND p.group_id = :groupId AND p.board = :board",
            new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("groupId", groupId)
                .addValue("board", board),
            (row, rowNumber) -> post(row)
        ).stream().findFirst();
    }

    /** Newest first. With authorEmail, only that person's posts; null lists the whole board. */
    public List<PostRow> listPosts(Long groupId, String board, String authorEmail, int limit, int offset) {
        return jdbcTemplate.query(
            "SELECT " + POST_COLUMNS + "FROM board_posts p "
                + "WHERE p.group_id = :groupId AND p.board = :board "
                + "AND (CAST(:authorEmail AS varchar) IS NULL OR p.author_email = :authorEmail) "
                + "ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset",
            scope(groupId, board, authorEmail).addValue("limit", limit).addValue("offset", offset),
            (row, rowNumber) -> post(row)
        );
    }

    public long countPosts(Long groupId, String board, String authorEmail) {
        Long found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM board_posts p WHERE p.group_id = :groupId AND p.board = :board "
                + "AND (CAST(:authorEmail AS varchar) IS NULL OR p.author_email = :authorEmail)",
            scope(groupId, board, authorEmail),
            Long.class
        );
        return found == null ? 0 : found;
    }

    public long countPostsSince(Long groupId, String board, String authorEmail, OffsetDateTime since) {
        Long found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM board_posts WHERE group_id = :groupId AND board = :board "
                + "AND author_email = :authorEmail AND created_at >= :since",
            scope(groupId, board, authorEmail).addValue("since", since, Types.TIMESTAMP_WITH_TIMEZONE),
            Long.class
        );
        return found == null ? 0 : found;
    }

    public void updatePost(Long postId, String title, String content) {
        jdbcTemplate.update(
            "UPDATE board_posts SET title = :title, content = :content, updated_at = now() WHERE id = :postId",
            new MapSqlParameterSource().addValue("postId", postId).addValue("title", title).addValue("content", content)
        );
    }

    /** Comments, likes and views of the post go with it (ON DELETE CASCADE). */
    public void deletePost(Long postId) {
        jdbcTemplate.update("DELETE FROM board_posts WHERE id = :postId", new MapSqlParameterSource("postId", postId));
    }

    public long insertComment(Long postId, String authorEmail, String content) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO board_comments (post_id, author_email, content) VALUES (:postId, :authorEmail, :content)",
            new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("authorEmail", authorEmail)
                .addValue("content", content),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Board comment id was not returned");
        }
        return id.longValue();
    }

    public List<CommentRow> listComments(Long postId) {
        return jdbcTemplate.query(
            "SELECT id, post_id, author_email, content, created_at FROM board_comments "
                + "WHERE post_id = :postId ORDER BY id ASC",
            new MapSqlParameterSource("postId", postId),
            (row, rowNumber) -> comment(row)
        );
    }

    public Optional<CommentRow> findComment(Long postId, Long commentId) {
        return jdbcTemplate.query(
            "SELECT id, post_id, author_email, content, created_at FROM board_comments "
                + "WHERE id = :commentId AND post_id = :postId",
            new MapSqlParameterSource().addValue("commentId", commentId).addValue("postId", postId),
            (row, rowNumber) -> comment(row)
        ).stream().findFirst();
    }

    public void deleteComment(Long commentId) {
        jdbcTemplate.update(
            "DELETE FROM board_comments WHERE id = :commentId",
            new MapSqlParameterSource("commentId", commentId)
        );
    }

    public void like(Long postId, String email) {
        jdbcTemplate.update(
            "INSERT INTO board_post_likes (post_id, liker_email) VALUES (:postId, :email) "
                + "ON CONFLICT (post_id, liker_email) DO NOTHING",
            person(postId, email)
        );
    }

    public void unlike(Long postId, String email) {
        jdbcTemplate.update(
            "DELETE FROM board_post_likes WHERE post_id = :postId AND liker_email = :email",
            person(postId, email)
        );
    }

    public boolean hasLiked(Long postId, String email) {
        Long found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM board_post_likes WHERE post_id = :postId AND liker_email = :email",
            person(postId, email),
            Long.class
        );
        return found != null && found > 0;
    }

    public void recordView(Long postId, String email) {
        jdbcTemplate.update(
            "INSERT INTO board_post_views (post_id, viewer_email) VALUES (:postId, :email) "
                + "ON CONFLICT (post_id, viewer_email) DO NOTHING",
            person(postId, email)
        );
    }

    private MapSqlParameterSource scope(Long groupId, String board, String authorEmail) {
        return new MapSqlParameterSource()
            .addValue("groupId", groupId)
            .addValue("board", board)
            .addValue("authorEmail", authorEmail, Types.VARCHAR);
    }

    private MapSqlParameterSource person(Long postId, String email) {
        return new MapSqlParameterSource().addValue("postId", postId).addValue("email", email);
    }

    private static PostRow post(ResultSet row) throws SQLException {
        return new PostRow(
            row.getLong("id"),
            row.getString("board"),
            row.getString("title"),
            row.getString("content"),
            row.getString("author_email"),
            row.getObject("created_at", OffsetDateTime.class),
            row.getObject("updated_at", OffsetDateTime.class),
            row.getLong("comment_count"),
            row.getLong("like_count"),
            row.getLong("view_count")
        );
    }

    private static CommentRow comment(ResultSet row) throws SQLException {
        return new CommentRow(
            row.getLong("id"),
            row.getLong("post_id"),
            row.getString("author_email"),
            row.getString("content"),
            row.getObject("created_at", OffsetDateTime.class)
        );
    }
}
