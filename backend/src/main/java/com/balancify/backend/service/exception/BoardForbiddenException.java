package com.balancify.backend.service.exception;

public class BoardForbiddenException extends RuntimeException {

    public BoardForbiddenException(String message) {
        super(message);
    }
}
