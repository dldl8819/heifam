package com.balancify.backend.repository;

import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Emails grouped as one person (access_people, access_person_emails). Emails are stored normalized. */
@Repository
public class AccessPersonRepository {

    public record PersonEmailRow(Long personId, String displayName, String email) {
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccessPersonRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every person with their emails, by name. */
    public List<PersonEmailRow> listAll() {
        return jdbcTemplate.query(
            "SELECT p.id, p.display_name, e.normalized_email FROM access_people p "
                + "JOIN access_person_emails e ON e.person_id = p.id "
                + "ORDER BY p.display_name, p.id, e.normalized_email",
            new MapSqlParameterSource(),
            (row, rowNumber) -> new PersonEmailRow(
                row.getLong("id"),
                row.getString("display_name"),
                row.getString("normalized_email")
            )
        );
    }

    /** The person's name for each of these emails that is grouped; the others are left out. */
    public Map<String, String> displayNamesOf(Collection<String> emails) {
        Map<String, String> names = new HashMap<>();
        if (emails == null || emails.isEmpty()) {
            return names;
        }
        jdbcTemplate.query(
            "SELECT e.normalized_email, p.display_name FROM access_person_emails e "
                + "JOIN access_people p ON p.id = e.person_id WHERE e.normalized_email IN (:emails)",
            new MapSqlParameterSource("emails", emails),
            row -> {
                names.put(row.getString("normalized_email"), row.getString("display_name"));
            }
        );
        return names;
    }

    /** The emails grouped with this one as one person, itself included; just itself when it is not grouped. */
    public List<String> samePersonEmails(String email) {
        List<String> emails = jdbcTemplate.queryForList(
            PersonSql.SAME_PERSON,
            new MapSqlParameterSource("email", email),
            String.class
        );
        return emails.isEmpty() ? List.of(email) : emails;
    }

    /** The person each of these emails belongs to; ungrouped emails are left out. */
    public Map<String, Long> personIdsOf(Collection<String> emails) {
        Map<String, Long> ids = new HashMap<>();
        if (emails == null || emails.isEmpty()) {
            return ids;
        }
        jdbcTemplate.query(
            "SELECT normalized_email, person_id FROM access_person_emails WHERE normalized_email IN (:emails)",
            new MapSqlParameterSource("emails", emails),
            row -> {
                ids.put(row.getString("normalized_email"), row.getLong("person_id"));
            }
        );
        return ids;
    }

    /** Locks these people for the rest of the transaction, in id order. */
    public void lockPeople(Collection<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return;
        }
        jdbcTemplate.queryForList(
            "SELECT id FROM access_people WHERE id IN (:ids) ORDER BY id FOR UPDATE",
            new MapSqlParameterSource("ids", personIds),
            Long.class
        );
    }

    public long createPerson(String displayName, String createdByEmail) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO access_people (display_name, created_by_email) VALUES (:displayName, :createdByEmail)",
            new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("createdByEmail", createdByEmail, Types.VARCHAR),
            keyHolder,
            new String[] {"id"}
        );
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Person id was not returned");
        }
        return id.longValue();
    }

    /** Puts these emails with the person, taking them from any person they were with. */
    public void attach(Long personId, Collection<String> emails) {
        for (String email : emails) {
            jdbcTemplate.update(
                "INSERT INTO access_person_emails (normalized_email, person_id) VALUES (:email, :personId) "
                    + "ON CONFLICT (normalized_email) DO UPDATE SET person_id = EXCLUDED.person_id",
                new MapSqlParameterSource().addValue("email", email).addValue("personId", personId)
            );
        }
    }

    public boolean rename(Long personId, String displayName) {
        return jdbcTemplate.update(
            "UPDATE access_people SET display_name = :displayName, updated_at = now() WHERE id = :id",
            new MapSqlParameterSource().addValue("displayName", displayName).addValue("id", personId)
        ) == 1;
    }

    public Optional<String> displayNameOf(Long personId) {
        return jdbcTemplate.queryForList(
            "SELECT display_name FROM access_people WHERE id = :id",
            new MapSqlParameterSource("id", personId),
            String.class
        ).stream().findFirst();
    }

    /** Takes the email out of its person; the person it was with, if any. */
    public Optional<Long> detach(String email) {
        List<Long> ids = jdbcTemplate.queryForList(
            "DELETE FROM access_person_emails WHERE normalized_email = :email RETURNING person_id",
            new MapSqlParameterSource("email", email),
            Long.class
        );
        return ids.stream().findFirst();
    }

    public int countEmails(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM access_person_emails WHERE person_id = :id",
            new MapSqlParameterSource("id", personId),
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public void deletePerson(Long personId) {
        jdbcTemplate.update("DELETE FROM access_people WHERE id = :id", new MapSqlParameterSource("id", personId));
    }
}
