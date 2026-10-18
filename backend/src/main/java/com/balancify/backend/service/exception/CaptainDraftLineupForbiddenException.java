package com.balancify.backend.service.exception;

/** A line-up handed in for a draft run by its captains by someone who is not one of its captains. */
public class CaptainDraftLineupForbiddenException extends RuntimeException {

    public CaptainDraftLineupForbiddenException(String message) {
        super(message);
    }
}
