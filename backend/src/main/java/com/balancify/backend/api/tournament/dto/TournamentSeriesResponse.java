package com.balancify.backend.api.tournament.dto;

import java.util.List;

public record TournamentSeriesResponse(
    Long seriesId,
    String round,
    int bracketSlot,
    String format,
    String status,
    int homeTeamNumber,
    int awayTeamNumber,
    int homeWins,
    int awayWins,
    Integer winnerTeamNumber,
    List<TournamentGameResponse> games
) {
}
