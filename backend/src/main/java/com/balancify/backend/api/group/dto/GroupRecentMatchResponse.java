package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GroupRecentMatchResponse(
    Long matchId,
    OffsetDateTime playedAt,
    String winningTeam,
    OffsetDateTime resultRecordedAt,
    String resultRecordedByNickname,
    List<GroupRecentMatchPlayerResponse> homeTeam,
    List<GroupRecentMatchPlayerResponse> awayTeam,
    int homeMmr,
    int awayMmr,
    int mmrDiff
) {
}
