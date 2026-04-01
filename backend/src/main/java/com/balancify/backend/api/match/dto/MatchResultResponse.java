package com.balancify.backend.api.match.dto;

import java.util.List;

public record MatchResultResponse(
    Long matchId,
    String winnerTeam,
    int kFactor,
    double homeExpectedWinRate,
    double awayExpectedWinRate,
    List<MatchResultParticipantResponse> participants
) {
}

