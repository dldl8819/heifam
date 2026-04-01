package com.balancify.backend.api.match.dto;

public record MatchResultParticipantResponse(
    Long playerId,
    String nickname,
    String team,
    int mmrBefore,
    int mmrAfter,
    int mmrDelta
) {
}

