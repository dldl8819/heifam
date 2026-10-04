package com.balancify.backend.api.tournament.dto;

import java.util.List;

/**
 * One planned game of a series. status is PLAYED (result recorded), NEXT (its match waits for a
 * result), UPCOMING (not set up yet) or SKIPPED (the series ended before it).
 */
public record TournamentGameResponse(
    int gameNumber,
    String raceComposition,
    String status,
    Long matchId,
    String winnerTeam,
    List<TournamentGamePlayerResponse> homePlayers,
    List<TournamentGamePlayerResponse> awayPlayers
) {
}
