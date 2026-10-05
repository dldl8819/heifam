package com.balancify.backend.api.points.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MatchConfirmationResponse(
    Long matchId,
    String raceComposition,
    Integer seriesGameNumber,
    OffsetDateTime resultRecordedAt,
    OffsetDateTime confirmDeadline,
    List<MatchConfirmationPlayerResponse> homePlayers,
    List<MatchConfirmationPlayerResponse> awayPlayers,
    String winnerTeam,
    String myTeam,
    boolean confirmed
) {
}
