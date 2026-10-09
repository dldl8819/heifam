package com.balancify.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The words and limits a vote on a notice is kept in: whether the notice has one (NONE, OPEN,
 * CLOSED) and what its options may be. A vote for or against is a vote with the two options 찬성
 * and 반대, which is also what a notice gets when it asks for a vote without naming any.
 */
public final class NoticeVotes {

    public static final String NONE = "NONE";
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    // What pages loaded before options existed still send and read.
    public static final String AGREE = "AGREE";
    public static final String DISAGREE = "DISAGREE";
    public static final String AGREE_LABEL = "찬성";
    public static final String DISAGREE_LABEL = "반대";

    public static final List<String> DEFAULT_OPTIONS = List.of(AGREE_LABEL, DISAGREE_LABEL);
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;
    public static final int MAX_OPTION_LENGTH = 50;

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

    static boolean asksForVote(String status) {
        return OPEN.equals(status) || CLOSED.equals(status);
    }

    /** The option an older page means by AGREE or DISAGREE. */
    static String legacyLabel(String value) {
        String choice = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (AGREE.equals(choice)) {
            return AGREE_LABEL;
        }
        if (DISAGREE.equals(choice)) {
            return DISAGREE_LABEL;
        }
        throw new IllegalArgumentException("투표할 항목을 골라 주세요.");
    }

    /** One option as it is stored: trimmed, not empty, within the length an option may have. */
    static String requireOptionLabel(String value) {
        String label = value == null ? "" : value.trim();
        if (label.isEmpty() || label.length() > MAX_OPTION_LENGTH) {
            throw new IllegalArgumentException("투표 항목은 1~" + MAX_OPTION_LENGTH + "자로 입력해 주세요.");
        }
        return label;
    }

    /**
     * The options of a vote as its writer gave them: empty entries are dropped, the rest trimmed.
     * There must be at least two and no more than a vote can hold, and no two alike, whatever
     * their case. Given none at all, the vote is for or against.
     */
    static List<String> requireOptions(List<String> values) {
        if (values == null) {
            return DEFAULT_OPTIONS;
        }
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String label = requireOptionLabel(value);
            if (!seen.add(label.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("같은 투표 항목이 두 번 들어 있습니다.");
            }
            labels.add(label);
        }
        if (labels.size() < MIN_OPTIONS || labels.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("투표 항목은 " + MIN_OPTIONS + "~" + MAX_OPTIONS + "개여야 합니다.");
        }
        return List.copyOf(labels);
    }
}
