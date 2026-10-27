package com.balancify.backend.repository;

import java.time.OffsetDateTime;

/**
 * A notice as its list shows it: everything but its text. The text is the bulk of a notice and a
 * list never shows it, so it is not read from the database for one.
 */
public record NoticeListRow(
    Long id,
    String title,
    String authorEmail,
    OffsetDateTime createdAt,
    boolean adminOnly,
    int revision,
    String voteStatus
) {
}
