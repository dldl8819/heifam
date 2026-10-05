package com.balancify.backend.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and likes are one row per person and notice; an insert that finds the row already there
 * does nothing, so double clicks and parallel tabs never fail. A read counts while it is not older
 * than the notice's latest revision (notices.revised_at), so a re-announced notice turns unread.
 */
@Repository
public class NoticeEngagementRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NoticeEngagementRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Marks the notice's current revision read. Returns whether this call did it: false when the
     * person had read this revision already, also when a parallel request got there first. An
     * earlier read of an older revision is moved up to readAt; a current one is left as it is.
     */
    public boolean markRead(Long noticeId, String email, OffsetDateTime readAt, OffsetDateTime revisedAt) {
        return jdbcTemplate.update(
            "INSERT INTO notice_reads (notice_id, reader_email, read_at) VALUES (:noticeId, :email, :readAt) "
                + "ON CONFLICT (notice_id, reader_email) DO UPDATE SET read_at = EXCLUDED.read_at "
                + "WHERE notice_reads.read_at < CAST(:revisedAt AS timestamptz)",
            params(noticeId, email)
                .addValue("readAt", readAt, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("revisedAt", revisedAt, Types.TIMESTAMP_WITH_TIMEZONE)
        ) > 0;
    }

    public Set<Long> findReadNoticeIds(String email, Collection<Long> noticeIds) {
        if (noticeIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT r.notice_id FROM notice_reads r JOIN notices n ON n.id = r.notice_id "
                + "WHERE r.reader_email = :email AND r.notice_id IN (:noticeIds) "
                + "AND (n.revised_at IS NULL OR r.read_at >= n.revised_at)",
            new MapSqlParameterSource().addValue("email", email).addValue("noticeIds", noticeIds),
            Long.class
        );
        return new HashSet<>(ids);
    }

    public void like(Long noticeId, String email) {
        jdbcTemplate.update(
            "INSERT INTO notice_likes (notice_id, liker_email) VALUES (:noticeId, :email) "
                + "ON CONFLICT (notice_id, liker_email) DO NOTHING",
            params(noticeId, email)
        );
    }

    public void unlike(Long noticeId, String email) {
        jdbcTemplate.update(
            "DELETE FROM notice_likes WHERE notice_id = :noticeId AND liker_email = :email",
            params(noticeId, email)
        );
    }

    public boolean hasLiked(Long noticeId, String email) {
        Integer found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notice_likes WHERE notice_id = :noticeId AND liker_email = :email",
            params(noticeId, email),
            Integer.class
        );
        return found != null && found > 0;
    }

    public Map<Long, Long> countLikes(Collection<Long> noticeIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (noticeIds.isEmpty()) {
            return counts;
        }
        jdbcTemplate.query(
            "SELECT notice_id, count(*) AS total FROM notice_likes WHERE notice_id IN (:noticeIds) GROUP BY notice_id",
            new MapSqlParameterSource("noticeIds", noticeIds),
            row -> {
                counts.put(row.getLong("notice_id"), row.getLong("total"));
            }
        );
        return counts;
    }

    private MapSqlParameterSource params(Long noticeId, String email) {
        return new MapSqlParameterSource().addValue("noticeId", noticeId).addValue("email", email);
    }
}
