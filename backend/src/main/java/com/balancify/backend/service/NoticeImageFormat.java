package com.balancify.backend.service;

import java.util.Optional;

/**
 * The kinds of image a notice may carry. Which one a file is gets read from its first bytes, never
 * from what the upload says it is, and that is the type it is later served as.
 */
enum NoticeImageFormat {
    PNG("image/png"),
    JPEG("image/jpeg"),
    WEBP("image/webp");

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    private final String contentType;

    NoticeImageFormat(String contentType) {
        this.contentType = contentType;
    }

    String contentType() {
        return contentType;
    }

    static Optional<NoticeImageFormat> detect(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (startsWith(bytes, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0, ascii("RIFF")) && startsWith(bytes, 8, ascii("WEBP"))) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
