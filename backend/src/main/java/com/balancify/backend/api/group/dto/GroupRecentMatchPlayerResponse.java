package com.balancify.backend.api.group.dto;

public record GroupRecentMatchPlayerResponse(
    Long playerId,
    String nickname,
    String team,
    int mmr
) {
}
