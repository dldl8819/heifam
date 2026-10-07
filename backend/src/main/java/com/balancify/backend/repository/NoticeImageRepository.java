package com.balancify.backend.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Images for notices. A row is unplaced (no notice) from its upload until the notice that names it
 * is saved. Where an image is placed is read apart from its bytes, so who may see it is settled
 * before the file is loaded.
 */
@Repository
public class NoticeImageRepository {

    /** The notice an image is placed in, if any, and whether that notice is kept to admins. */
    public record Placement(Long noticeId, boolean noticeAdminOnly) {
    }

    public record StoredImage(String contentType, byte[] data) {
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NoticeImageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(Long groupId, String contentType, byte[] data) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO notice_images (group_id, content_type, byte_size, data) "
                + "VALUES (:groupId, :contentType, :byteSize, :data)",
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("contentType", contentType)
                .addValue("byteSize", data.length)
                .addValue("data", data, Types.BINARY),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Notice image id was not returned");
        }
        return id.longValue();
    }

    public Optional<Placement> findPlacement(Long groupId, Long imageId) {
        List<Placement> found = jdbcTemplate.query(
            "SELECT i.notice_id, COALESCE(n.admin_only, false) AS admin_only "
                + "FROM notice_images i LEFT JOIN notices n ON n.id = i.notice_id "
                + "WHERE i.id = :imageId AND i.group_id = :groupId",
            new MapSqlParameterSource().addValue("imageId", imageId).addValue("groupId", groupId),
            (row, rowNumber) -> {
                long noticeId = row.getLong("notice_id");
                return new Placement(row.wasNull() ? null : noticeId, row.getBoolean("admin_only"));
            }
        );
        return found.stream().findFirst();
    }

    public Optional<StoredImage> findImage(Long imageId) {
        List<StoredImage> found = jdbcTemplate.query(
            "SELECT content_type, data FROM notice_images WHERE id = :imageId",
            new MapSqlParameterSource("imageId", imageId),
            (row, rowNumber) -> new StoredImage(row.getString("content_type"), row.getBytes("data"))
        );
        return found.stream().findFirst();
    }

    public int countUnplaced(Long groupId) {
        Integer found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notice_images WHERE group_id = :groupId AND notice_id IS NULL",
            new MapSqlParameterSource("groupId", groupId),
            Integer.class
        );
        return found == null ? 0 : found;
    }

    public int deleteUnplacedBefore(OffsetDateTime uploadedBefore) {
        return jdbcTemplate.update(
            "DELETE FROM notice_images WHERE notice_id IS NULL AND created_at < :uploadedBefore",
            new MapSqlParameterSource().addValue("uploadedBefore", uploadedBefore, Types.TIMESTAMP_WITH_TIMEZONE)
        );
    }

    /** Places the unplaced ones among these images in the notice; those placed already are left alone. */
    public void place(Long groupId, Long noticeId, Collection<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
            "UPDATE notice_images SET notice_id = :noticeId "
                + "WHERE id IN (:imageIds) AND group_id = :groupId AND notice_id IS NULL",
            new MapSqlParameterSource()
                .addValue("noticeId", noticeId)
                .addValue("groupId", groupId)
                .addValue("imageIds", imageIds)
        );
    }

    public int countPlaced(Long noticeId, Collection<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return 0;
        }
        Integer found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notice_images WHERE notice_id = :noticeId AND id IN (:imageIds)",
            new MapSqlParameterSource().addValue("noticeId", noticeId).addValue("imageIds", imageIds),
            Integer.class
        );
        return found == null ? 0 : found;
    }

    /** Removes the notice's images that its text no longer names. */
    public int deletePlacedExcept(Long noticeId, Collection<Long> keptImageIds) {
        if (keptImageIds.isEmpty()) {
            return jdbcTemplate.update(
                "DELETE FROM notice_images WHERE notice_id = :noticeId",
                new MapSqlParameterSource("noticeId", noticeId)
            );
        }
        return jdbcTemplate.update(
            "DELETE FROM notice_images WHERE notice_id = :noticeId AND id NOT IN (:keptImageIds)",
            new MapSqlParameterSource().addValue("noticeId", noticeId).addValue("keptImageIds", keptImageIds)
        );
    }
}
