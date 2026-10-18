package com.balancify.backend.service.exception;

/** A set of a regular draft that already has a result, changed by someone who may not edit results. */
public class CaptainDraftResultLockedException extends RuntimeException {

    public CaptainDraftResultLockedException(String message) {
        super(message);
    }
}
