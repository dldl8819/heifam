package com.balancify.backend.api.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchResultResponse(
    Long matchId,
    String winnerTeam,
    int kFactor,
    Double homeExpectedWinRate,
    Double awayExpectedWinRate,
    List<MatchResultParticipantResponse> participants
) {
}
