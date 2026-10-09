package com.balancify.backend.service;

import java.util.Locale;

/**
 * The words a vote on a notice is kept in: whether the notice has one (NONE, OPEN, CLOSED) and what
 * a member chose (AGREE, DISAGREE).
 */
public final class NoticeVotes {

    public static final String NONE = "NONE";
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    public static final String AGREE = "AGREE";
    public static final String DISAGREE = "DISAGREE";

    private NoticeVotes() {
    }

    /** A status as sent by the page; anything but the three words is refused. */
    static String requireStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!NONE.equals(status) && !OPEN.equals(status) && !CLOSED.equals(status)) {
            throw new IllegalArgumentException("Vote status must be NONE, OPEN or CLOSED");
        }
        return status;
    }

    static String requireChoice(String value) {
        String choice = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!AGREE.equals(choice) && !DISAGREE.equals(choice)) {
            throw new IllegalArgumentException("찬성 또는 반대를 골라 주세요.");
        }
        return choice;
    }
}
