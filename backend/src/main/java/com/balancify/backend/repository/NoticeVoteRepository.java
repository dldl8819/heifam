package com.balancify.backend.repository;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * The options of a notice's vote and the votes on them. A person has one vote per notice, which
 * points at one option; voting again moves it. Removing an option removes the votes on it
 * (ON DELETE CASCADE), and removing the notice removes both.
 */
@Repository
public class NoticeVoteRepository {

    public record OptionRow(Long id, String label, int position, long voteCount) {
    }

    public record VoterRow(Long optionId, String voterEmail) {
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NoticeVoteRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The options in the order they are shown, each with how many chose it. */
    public List<OptionRow> listOptions(Long noticeId) {
        return jdbcTemplate.query(
            "SELECT o.id, o.label, o.position, "
                + "(SELECT count(*) FROM notice_votes v WHERE v.option_id = o.id) AS vote_count "
                + "FROM notice_vote_options o WHERE o.notice_id = :noticeId ORDER BY o.position ASC, o.id ASC",
            new MapSqlParameterSource("noticeId", noticeId),
            (row, rowNumber) -> new OptionRow(
                row.getLong("id"), row.getString("label"), row.getInt("position"), row.getLong("vote_count")
            )
        );
    }

    /** Puts these options in place of whatever the notice had; any votes on the old ones go with them. */
    public void replaceOptions(Long noticeId, List<String> labels, String createdByEmail) {
        jdbcTemplate.update(
            "DELETE FROM notice_vote_options WHERE notice_id = :noticeId",
            new MapSqlParameterSource("noticeId", noticeId)
        );
        for (int position = 0; position < labels.size(); position++) {
            insertOption(noticeId, labels.get(position), position, createdByEmail);
        }
    }

    /**
     * Adds an option after the ones the notice has. Throws DuplicateKeyException when the notice
     * already has that option, in whatever case (uq_notice_vote_options_label).
     */
    public long addOption(Long noticeId, String label, String createdByEmail) {
        Integer next = jdbcTemplate.queryForObject(
            "SELECT coalesce(max(position), -1) + 1 FROM notice_vote_options WHERE notice_id = :noticeId",
            new MapSqlParameterSource("noticeId", noticeId),
            Integer.class
        );
        return insertOption(noticeId, label, next == null ? 0 : next, createdByEmail);
    }

    /** True when the option was the notice's and is now gone, with the votes on it. */
    public boolean removeOption(Long noticeId, Long optionId) {
        return jdbcTemplate.update(
            "DELETE FROM notice_vote_options WHERE id = :optionId AND notice_id = :noticeId",
            new MapSqlParameterSource().addValue("optionId", optionId).addValue("noticeId", noticeId)
        ) > 0;
    }

    /** One vote per person and notice: voting again moves it to the other option. */
    public void castVote(Long noticeId, String email, Long optionId) {
        jdbcTemplate.update(
            "INSERT INTO notice_votes (notice_id, voter_email, option_id) VALUES (:noticeId, :email, :optionId) "
                + "ON CONFLICT (notice_id, voter_email) DO UPDATE SET option_id = EXCLUDED.option_id, updated_at = now() "
                + "WHERE notice_votes.option_id <> EXCLUDED.option_id",
            voter(noticeId, email).addValue("optionId", optionId)
        );
    }

    public void withdrawVote(Long noticeId, String email) {
        jdbcTemplate.update(
            "DELETE FROM notice_votes WHERE notice_id = :noticeId AND voter_email = :email",
            voter(noticeId, email)
        );
    }

    /** The option the person voted for on the notice, or empty when they have not voted. */
    public Optional<Long> findVotedOptionId(Long noticeId, String email) {
        return jdbcTemplate.queryForList(
            "SELECT option_id FROM notice_votes WHERE notice_id = :noticeId AND voter_email = :email",
            voter(noticeId, email),
            Long.class
        ).stream().findFirst();
    }

    /** Who voted for what, in the order they voted. Only a vote that is not anonymous may show it. */
    public List<VoterRow> listVoters(Long noticeId) {
        return jdbcTemplate.query(
            "SELECT option_id, voter_email FROM notice_votes WHERE notice_id = :noticeId ORDER BY updated_at ASC, id ASC",
            new MapSqlParameterSource("noticeId", noticeId),
            (row, rowNumber) -> new VoterRow(row.getLong("option_id"), row.getString("voter_email"))
        );
    }

    private long insertOption(Long noticeId, String label, int position, String createdByEmail) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO notice_vote_options (notice_id, label, position, created_by_email) "
                + "VALUES (:noticeId, :label, :position, :createdByEmail)",
            new MapSqlParameterSource()
                .addValue("noticeId", noticeId)
                .addValue("label", label)
                .addValue("position", position)
                .addValue("createdByEmail", createdByEmail, Types.VARCHAR),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Vote option id was not returned");
        }
        return id.longValue();
    }

    private MapSqlParameterSource voter(Long noticeId, String email) {
        return new MapSqlParameterSource().addValue("noticeId", noticeId).addValue("email", email);
    }
}
