package com.balancify.backend.service.exception;

/** The request cannot be taken as things stand: one is already waiting, or this one is no longer waiting. */
public class NicknameRequestConflictException extends RuntimeException {

    public NicknameRequestConflictException(String message) {
        super(message);
    }
}
