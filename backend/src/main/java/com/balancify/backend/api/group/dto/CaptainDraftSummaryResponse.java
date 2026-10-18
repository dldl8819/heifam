package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

/**
 * One regular draft in the list of records: when, who started it (the nickname, never the email),
 * the captains, and how it went. A round goes to the team that won more of its sets once all of
 * them have a result; a level round goes to nobody. roundCount is the regular rounds; an ace decider
 * (aceDecider) counts as one more round won by whoever won it.
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
    int totalSets,
    String createdByNickname,
    boolean aceDecider
) {
}
