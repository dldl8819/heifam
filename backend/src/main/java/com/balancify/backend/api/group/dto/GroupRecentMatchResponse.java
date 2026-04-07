package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupRecentMatchResponse(
    Long matchId,
    OffsetDateTime playedAt,
    String status,
    String winningTeam,
    OffsetDateTime resultRecordedAt,
    String resultRecordedByNickname,
    String homeRaceComposition,
    String awayRaceComposition,
    List<GroupRecentMatchPlayerResponse> homeTeam,
    List<GroupRecentMatchPlayerResponse> awayTeam,
    Integer homeMmr,
    Integer awayMmr,
    Integer mmrDiff
) {
}
