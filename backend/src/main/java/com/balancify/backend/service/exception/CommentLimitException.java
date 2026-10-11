package com.balancify.backend.service.exception;

/** More comments in a day than one person may write. */
public class CommentLimitException extends RuntimeException {

    public CommentLimitException(String message) {
        super(message);
    }
}
