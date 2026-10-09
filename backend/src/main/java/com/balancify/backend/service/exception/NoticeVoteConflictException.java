package com.balancify.backend.service.exception;

/**
 * The vote is no longer as the request took it to be: the option was removed, it exists already,
 * or votes were cast that the change would break.
 */
public class NoticeVoteConflictException extends RuntimeException {

    public NoticeVoteConflictException(String message) {
        super(message);
    }
}
