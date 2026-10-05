package com.balancify.backend.service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Times for notice revisions and reads. A read counts for a revision when it is not older than
 * the revision, so both come from this one clock, cut to the precision the database keeps.
 */
final class NoticeRevisions {

    private NoticeRevisions() {
    }

    static OffsetDateTime now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** When a read of the given revision happened: now, and never before the revision itself. */
    static OffsetDateTime readTime(OffsetDateTime revisedAt) {
        OffsetDateTime now = now();
        return revisedAt != null && now.isBefore(revisedAt) ? revisedAt : now;
    }
}
