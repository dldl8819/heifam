package com.balancify.backend.service.exception;

/** An image that cannot be taken, or a notice whose text names an image it cannot show. */
public class NoticeImageException extends RuntimeException {

    public enum Reason {
        // Not a PNG, JPEG or WebP file.
        UNSUPPORTED,
        TOO_LARGE,
        // Too many uploaded images are waiting for a notice.
        TOO_MANY_WAITING,
        TOO_MANY_IN_NOTICE,
        // The text names an image that is gone or belongs to another notice.
        UNAVAILABLE
    }

    private final Reason reason;

    public NoticeImageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
