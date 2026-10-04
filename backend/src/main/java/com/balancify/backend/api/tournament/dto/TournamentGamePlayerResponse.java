package com.balancify.backend.api.tournament.dto;

public record TournamentGamePlayerResponse(
    Long playerId,
    String nickname,
    String assignedRace
) {
}
