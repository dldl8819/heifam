package com.balancify.backend.service.exception;

public class PlayerEditForbiddenException extends RuntimeException {

    public PlayerEditForbiddenException(String message) {
        super(message);
    }
}
