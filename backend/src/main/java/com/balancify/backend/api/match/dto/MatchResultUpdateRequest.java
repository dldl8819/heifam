package com.balancify.backend.api.match.dto;

public record MatchResultUpdateRequest(
    String winnerTeam,
    String raceComposition
) {
}
