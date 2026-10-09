package com.balancify.backend.service.exception;

/** More posts or requests in a day than one person may make. */
public class BoardLimitException extends RuntimeException {

    public BoardLimitException(String message) {
        super(message);
    }
}
