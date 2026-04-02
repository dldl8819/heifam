package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupRecentMatchPlayerResponse(
    Long playerId,
    String nickname,
    String team,
    Integer mmr
) {
}
