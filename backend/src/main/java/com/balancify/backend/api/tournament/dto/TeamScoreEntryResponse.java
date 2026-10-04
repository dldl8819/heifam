package com.balancify.backend.api.tournament.dto;

/**
 * One player's team record: placement points from finished tournaments (first 3, second 2,
 * third 1), how often they placed, their series record, and their own game record to compare.
 * Win rates are percentages with one decimal, or null with nothing to count.
 */
public record TeamScoreEntryResponse(
    int rank,
    Long playerId,
    String nickname,
    int points,
    int championships,
    int runnerUps,
    int thirdPlaces,
    int tournaments,
    int seriesWins,
    int seriesLosses,
    Double seriesWinRate,
    int wins,
    int losses,
    Double winRate
) {
}
