package com.balancify.backend.service.exception;

public class AccountDeletionException extends RuntimeException {

    /** Why an account could not be removed, so callers can tell the admin what to do. */
    public enum Reason {
        // The email is in ADMIN_EMAILS, SUPER_ADMIN_EMAILS or ALLOWED_USER_EMAILS.
        CONFIGURED_ACCESS_LIST,
        // The player's login account could not be told apart from others.
        IDENTITY_UNRESOLVED,
        // The player is linked to a login account that has no email on record.
        LINKED_ACCOUNT_WITHOUT_EMAIL,
        // Another player not yet anonymized has the same nickname, so the account's owner is unclear.
        NICKNAME_SHARED_BY_PLAYERS,
        // More than one email in the access lists carries the player's nickname.
        NICKNAME_SHARED_BY_ACCOUNTS,
        // Supabase Auth could not be reached or refused the deletion.
        AUTH_UNAVAILABLE,
        OTHER
    }

    private final Reason reason;

    public AccountDeletionException(String message) {
        this(Reason.OTHER, message);
    }

    public AccountDeletionException(Reason reason, String message) {
        super(message);
        this.reason = reason == null ? Reason.OTHER : reason;
    }

    public Reason getReason() {
        return reason;
    }
}
