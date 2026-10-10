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

/** The records of prize draws: one row per draw and one per winner. Winners go with their draw. */
@Repository
public class PrizeDrawRepository {

    public record DrawRow(
        Long id,
        String title,
        String mode,
        int entrantCount,
        String createdByEmail,
        OffsetDateTime createdAt
    ) {
    }

    public record WinnerRow(Long drawId, int place, String name, String prize) {
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PrizeDrawRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertDraw(Long groupId, String title, String mode, int entrantCount, String createdByEmail) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO prize_draws (group_id, title, mode, entrant_count, created_by_email) "
                + "VALUES (:groupId, :title, :mode, :entrantCount, :createdByEmail)",
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("title", title)
                .addValue("mode", mode)
                .addValue("entrantCount", entrantCount)
                .addValue("createdByEmail", createdByEmail),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Prize draw id was not returned");
        }
        return id.longValue();
    }

    public void insertWinner(Long drawId, int place, String name, Long playerId, String prize) {
        jdbcTemplate.update(
            "INSERT INTO prize_draw_winners (draw_id, place, name, player_id, prize) "
                + "VALUES (:drawId, :place, :name, :playerId, :prize)",
            new MapSqlParameterSource()
                .addValue("drawId", drawId)
                .addValue("place", place)
                .addValue("name", name)
                .addValue("playerId", playerId, Types.BIGINT)
                .addValue("prize", prize, Types.VARCHAR)
        );
    }

    /** Newest first. */
    public List<DrawRow> listDraws(Long groupId, int limit) {
        return jdbcTemplate.query(
            "SELECT id, title, mode, entrant_count, created_by_email, created_at FROM prize_draws "
                + "WHERE group_id = :groupId ORDER BY id DESC LIMIT :limit",
            new MapSqlParameterSource().addValue("groupId", groupId).addValue("limit", limit),
            (row, rowNumber) -> new DrawRow(
                row.getLong("id"),
                row.getString("title"),
                row.getString("mode"),
                row.getInt("entrant_count"),
                row.getString("created_by_email"),
                row.getObject("created_at", OffsetDateTime.class)
            )
        );
    }

    public Optional<DrawRow> findDraw(Long groupId, Long drawId) {
        return jdbcTemplate.query(
            "SELECT id, title, mode, entrant_count, created_by_email, created_at FROM prize_draws "
                + "WHERE id = :drawId AND group_id = :groupId",
            new MapSqlParameterSource().addValue("drawId", drawId).addValue("groupId", groupId),
            (row, rowNumber) -> new DrawRow(
                row.getLong("id"),
                row.getString("title"),
                row.getString("mode"),
                row.getInt("entrant_count"),
                row.getString("created_by_email"),
                row.getObject("created_at", OffsetDateTime.class)
            )
        ).stream().findFirst();
    }

    /** The winners of these draws, by draw and place. */
    public List<WinnerRow> listWinners(Collection<Long> drawIds) {
        if (drawIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
            "SELECT draw_id, place, name, prize FROM prize_draw_winners WHERE draw_id IN (:drawIds) ORDER BY draw_id, place",
            new MapSqlParameterSource("drawIds", drawIds),
            (row, rowNumber) -> new WinnerRow(row.getLong("draw_id"), row.getInt("place"), row.getString("name"), row.getString("prize"))
        );
    }

    /** How many of these ids are players of the group. */
    public long countGroupPlayers(Long groupId, Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return 0;
        }
        Long found = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM players WHERE group_id = :groupId AND id IN (:playerIds)",
            new MapSqlParameterSource().addValue("groupId", groupId).addValue("playerIds", playerIds),
            Long.class
        );
        return found == null ? 0 : found;
    }

    /** The winners go with it (ON DELETE CASCADE). */
    public void deleteDraw(Long drawId) {
        jdbcTemplate.update("DELETE FROM prize_draws WHERE id = :drawId", new MapSqlParameterSource("drawId", drawId));
    }
}
