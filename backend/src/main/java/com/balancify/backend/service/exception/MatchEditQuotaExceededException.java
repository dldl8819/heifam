package com.balancify.backend.service.exception;

public class MatchEditQuotaExceededException extends RuntimeException {

    public MatchEditQuotaExceededException(String message) {
        super(message);
    }
}
