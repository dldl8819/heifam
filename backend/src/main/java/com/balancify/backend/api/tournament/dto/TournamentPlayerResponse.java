package com.balancify.backend.api.tournament.dto;

public record TournamentPlayerResponse(
    Long playerId,
    String nickname,
    String race,
    Integer mmr
) {
}
