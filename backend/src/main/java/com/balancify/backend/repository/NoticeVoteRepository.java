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
 * points at one option, and voting again moves it; in a vote that takes more than one choice
 * (notices.vote_multiple) they have one vote on each option they chose. Removing an option removes the votes on it
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
    /**
     * A vote is one a person: when the email is grouped with others as one person (access_people),
     * a vote any of them gave this notice moves to this one. The person is locked first, so two of
     * their emails voting at once come one after the other.
     */
    public void castVote(Long noticeId, String email, Long optionId) {
        jdbcTemplate.queryForList(PersonSql.LOCK_PERSON, voter(noticeId, email), Long.class);
        // Whatever else the person had on this notice, under any of their emails, gives way.
        jdbcTemplate.update(
            "DELETE FROM notice_votes WHERE notice_id = :noticeId "
                + "AND " + PersonSql.samePerson("voter_email") + " "
                + "AND NOT (voter_email = :email AND option_id = :optionId)",
            voter(noticeId, email).addValue("optionId", optionId)
        );
        jdbcTemplate.update(
            "INSERT INTO notice_votes (notice_id, voter_email, option_id) VALUES (:noticeId, :email, :optionId) "
                + "ON CONFLICT (notice_id, voter_email, option_id) DO NOTHING",
            voter(noticeId, email).addValue("optionId", optionId)
        );
    }

    /**
     * Adds an option to what the person chose, in a vote that takes more than one. Their choices
     * made with another of their emails are brought under this one first, so a person holds each
     * option once and all their choices together.
     */
    public void addVote(Long noticeId, String email, Long optionId) {
        jdbcTemplate.queryForList(PersonSql.LOCK_PERSON, voter(noticeId, email), Long.class);
        jdbcTemplate.update(
            "UPDATE notice_votes SET voter_email = :email WHERE notice_id = :noticeId AND voter_email <> :email "
                + "AND voter_email IN (" + PersonSql.SAME_PERSON + ") "
                + "AND option_id NOT IN (SELECT option_id FROM notice_votes WHERE notice_id = :noticeId AND voter_email = :email)",
            voter(noticeId, email)
        );
        jdbcTemplate.update(
            "DELETE FROM notice_votes WHERE notice_id = :noticeId AND voter_email <> :email "
                + "AND voter_email IN (" + PersonSql.SAME_PERSON + ")",
            voter(noticeId, email)
        );
        jdbcTemplate.update(
            "INSERT INTO notice_votes (notice_id, voter_email, option_id) VALUES (:noticeId, :email, :optionId) "
                + "ON CONFLICT (notice_id, voter_email, option_id) DO NOTHING",
            voter(noticeId, email).addValue("optionId", optionId)
        );
    }

    /** Takes the person's vote off one option, whichever of their emails cast it. */
    public void removeVote(Long noticeId, String email, Long optionId) {
        jdbcTemplate.update(
            "DELETE FROM notice_votes WHERE notice_id = :noticeId AND option_id = :optionId "
                + "AND " + PersonSql.samePerson("voter_email"),
            voter(noticeId, email).addValue("optionId", optionId)
        );
    }

    /** Every option the person chose on the notice, with any of their emails, in the order of the options. */
    public List<Long> findVotedOptionIds(Long noticeId, String email) {
        return jdbcTemplate.queryForList(
            "SELECT v.option_id FROM notice_votes v JOIN notice_vote_options o ON o.id = v.option_id "
                + "WHERE v.notice_id = :noticeId AND " + PersonSql.samePerson("v.voter_email") + " "
                + "GROUP BY v.option_id, o.position ORDER BY o.position ASC, v.option_id ASC",
            voter(noticeId, email),
            Long.class
        );
    }

    /** How many people voted, however many options each chose. */
    public long countVoters(Long noticeId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT voter_email) FROM notice_votes WHERE notice_id = :noticeId",
            new MapSqlParameterSource("noticeId", noticeId),
            Long.class
        );
        return count == null ? 0 : count;
    }

    /** Whether anyone chose more than one option, which keeps a vote taking more than one. */
    public boolean hasVoterWithSeveralChoices(Long noticeId) {
        Boolean found = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM notice_votes WHERE notice_id = :noticeId GROUP BY voter_email HAVING count(*) > 1)",
            new MapSqlParameterSource("noticeId", noticeId),
            Boolean.class
        );
        return Boolean.TRUE.equals(found);
    }

    public void withdrawVote(Long noticeId, String email) {
        jdbcTemplate.update(
            "DELETE FROM notice_votes WHERE notice_id = :noticeId "
                + "AND " + PersonSql.samePerson("voter_email"),
            voter(noticeId, email)
        );
    }

    /** The option the person voted for on the notice, with any of their emails, or empty when they have not voted. */
    public Optional<Long> findVotedOptionId(Long noticeId, String email) {
        return jdbcTemplate.queryForList(
            "SELECT option_id FROM notice_votes WHERE notice_id = :noticeId "
                + "AND " + PersonSql.samePerson("voter_email") + " "
                + "ORDER BY updated_at DESC, id DESC LIMIT 1",
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
