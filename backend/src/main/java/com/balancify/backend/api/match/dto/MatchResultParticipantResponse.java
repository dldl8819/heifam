package com.balancify.backend.api.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchResultParticipantResponse(
    Long playerId,
    String nickname,
    String team,
    String assignedRace,
    Integer mmrBefore,
    Integer mmrAfter,
    Integer mmrDelta
) {
    public MatchResultParticipantResponse(
        Long playerId,
        String nickname,
        String team,
        Integer mmrBefore,
        Integer mmrAfter,
        Integer mmrDelta
    ) {
        this(playerId, nickname, team, null, mmrBefore, mmrAfter, mmrDelta);
    }
}
