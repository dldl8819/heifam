package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

/**
 * One regular draft in the list of records: when, the captains, and how it went. A round goes to
 * the team that won more of its sets once all of them have a result; a level round goes to nobody.
 */
public record CaptainDraftSummaryResponse(
    Long draftId,
    String title,
    OffsetDateTime createdAt,
    String status,
    String homeCaptainNickname,
    String awayCaptainNickname,
    int participantCount,
    int roundCount,
    int setsPerRound,
    int homeRoundWins,
    int awayRoundWins,
    int homeSetWins,
    int awaySetWins,
    int decidedSets,
    int totalSets
) {
}
