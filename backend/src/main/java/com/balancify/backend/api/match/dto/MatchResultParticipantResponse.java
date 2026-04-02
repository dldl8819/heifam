package com.balancify.backend.api.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchResultParticipantResponse(
    Long playerId,
    String nickname,
    String team,
    Integer mmrBefore,
    Integer mmrAfter,
    Integer mmrDelta
) {
}
