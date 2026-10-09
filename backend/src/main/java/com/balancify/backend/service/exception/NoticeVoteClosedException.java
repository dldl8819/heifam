package com.balancify.backend.service.exception;

/** A vote cast or taken back after the notice's vote was closed. */
public class NoticeVoteClosedException extends RuntimeException {

    public NoticeVoteClosedException(String message) {
        super(message);
    }
}
