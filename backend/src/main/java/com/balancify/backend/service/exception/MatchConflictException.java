package com.balancify.backend.service.exception;

public class MatchConflictException extends RuntimeException {

    public MatchConflictException(String message) {
        super(message);
    }
}
