package com.balancify.backend.api.tournament.dto;

import java.util.List;

public record TournamentTeamResponse(
    Long teamId,
    int teamNumber,
    Integer finalRank,
    Integer totalMmr,
    List<TournamentPlayerResponse> members
) {
}
