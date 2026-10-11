package com.balancify.backend.repository;

/**
 * SQL for things done once a person (votes, likes, reads, predictions) when a person signs in with
 * several emails grouped as one (access_people). Each query binds the email as :email; an email
 * that is not grouped is a person of its own, as before.
 */
public final class PersonSql {

    /** The emails grouped with :email as one person, :email included; no rows when it is not grouped. */
    public static final String SAME_PERSON =
        "SELECT other.normalized_email FROM access_person_emails mine "
            + "JOIN access_person_emails other ON other.person_id = mine.person_id "
            + "WHERE mine.normalized_email = :email";

    /**
     * Locks the person :email belongs to until the transaction ends, so that two of their emails
     * acting at once come one after the other. Nothing to lock when the email is not grouped.
     */
    public static final String LOCK_PERSON =
        "SELECT p.id FROM access_people p JOIN access_person_emails e ON e.person_id = p.id "
            + "WHERE e.normalized_email = :email FOR UPDATE OF p";

    private PersonSql() {
    }

    /** column is :email, or another email of the same person. */
    public static String samePerson(String column) {
        return "(" + column + " = :email OR " + column + " IN (" + SAME_PERSON + "))";
    }
}
