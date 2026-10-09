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
 * Requests for a nickname change. A request leaves PENDING once, by its owner calling it off or an
 * admin deciding it: both updates take only a row that is still PENDING, so two of them at the same
 * moment cannot both win.
 */
@Repository
public class NicknameRequestRepository {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELED = "CANCELED";

    public record RequestRow(
        Long id,
        String requesterEmail,
        String currentNickname,
        String desiredNickname,
        String reason,
        String status,
        String adminNote,
        String processedByEmail,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt
    ) {
    }

    private static final String COLUMNS =
        "id, requester_email, current_nickname, desired_nickname, reason, status, admin_note, "
            + "processed_by_email, processed_at, created_at ";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NicknameRequestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Throws DuplicateKeyException when the person already has a PENDING request for the group
     * (uq_nickname_change_requests_pending).
     */
    public long insert(Long groupId, String requesterEmail, String currentNickname, String desiredNickname, String reason) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO nickname_change_requests (group_id, requester_email, current_nickname, desired_nickname, reason) "
                + "VALUES (:groupId, :requesterEmail, :currentNickname, :desiredNickname, :reason)",
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("requesterEmail", requesterEmail)
                .addValue("currentNickname", currentNickname, Types.VARCHAR)
                .addValue("desiredNickname", desiredNickname)
                .addValue("reason", reason, Types.VARCHAR),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Nickname request id was not returned");
        }
        return id.longValue();
    }

    public Optional<RequestRow> find(Long groupId, Long requestId) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + "FROM nickname_change_requests WHERE id = :requestId AND group_id = :groupId",
            new MapSqlParameterSource().addValue("requestId", requestId).addValue("groupId", groupId),
            (row, rowNumber) -> request(row)
        ).stream().findFirst();
    }

    /** One person's requests, newest first. */
    public List<RequestRow> listByRequester(Long groupId, String requesterEmail, int limit) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + "FROM nickname_change_requests "
                + "WHERE group_id = :groupId AND requester_email = :requesterEmail "
                + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("requesterEmail", requesterEmail)
                .addValue("limit", limit),
            (row, rowNumber) -> request(row)
        );
    }

    /**
     * Everyone's requests as the admins work through them: those still waiting first, the one that
     * has waited longest on top, then the rest, newest first. Requests called off by their owner are
     * left out: there is nothing for an admin to do with them.
     */
    public List<RequestRow> listForAdmins(Long groupId, int limit) {
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + "FROM nickname_change_requests "
                + "WHERE group_id = :groupId AND status <> '" + STATUS_CANCELED + "' "
                + "ORDER BY (status = '" + STATUS_PENDING + "') DESC, "
                + "CASE WHEN status = '" + STATUS_PENDING + "' THEN created_at END ASC, "
                + "created_at DESC, id DESC LIMIT :limit",
            new MapSqlParameterSource().addValue("groupId", groupId).addValue("limit", limit),
            (row, rowNumber) -> request(row)
        );
    }

    public long countSince(Long groupId, String requesterEmail, OffsetDateTime since) {
        Long found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM nickname_change_requests WHERE group_id = :groupId "
                + "AND requester_email = :requesterEmail AND created_at >= :since",
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("requesterEmail", requesterEmail)
                .addValue("since", since, Types.TIMESTAMP_WITH_TIMEZONE),
            Long.class
        );
        return found == null ? 0 : found;
    }

    /** True when the request was still waiting and is now called off. */
    public boolean cancel(Long requestId) {
        return jdbcTemplate.update(
            "UPDATE nickname_change_requests SET status = '" + STATUS_CANCELED + "', processed_at = now() "
                + "WHERE id = :requestId AND status = '" + STATUS_PENDING + "'",
            new MapSqlParameterSource("requestId", requestId)
        ) > 0;
    }

    /** True when the request was still waiting and now carries the admin's decision. */
    public boolean decide(Long requestId, String status, String adminNote, String processedByEmail) {
        return jdbcTemplate.update(
            "UPDATE nickname_change_requests SET status = :status, admin_note = :adminNote, "
                + "processed_by_email = :processedByEmail, processed_at = now() "
                + "WHERE id = :requestId AND status = '" + STATUS_PENDING + "'",
            new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("status", status)
                .addValue("adminNote", adminNote, Types.VARCHAR)
                .addValue("processedByEmail", processedByEmail)
        ) > 0;
    }

    private static RequestRow request(ResultSet row) throws SQLException {
        return new RequestRow(
            row.getLong("id"),
            row.getString("requester_email"),
            row.getString("current_nickname"),
            row.getString("desired_nickname"),
            row.getString("reason"),
            row.getString("status"),
            row.getString("admin_note"),
            row.getString("processed_by_email"),
            row.getObject("processed_at", OffsetDateTime.class),
            row.getObject("created_at", OffsetDateTime.class)
        );
    }
}
