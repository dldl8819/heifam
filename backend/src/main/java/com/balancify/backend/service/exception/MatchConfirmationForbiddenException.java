package com.balancify.backend.service.exception;

public class MatchConfirmationForbiddenException extends RuntimeException {

    public MatchConfirmationForbiddenException(String message) {
        super(message);
    }
}
