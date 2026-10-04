package com.balancify.backend.api.tournament.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TeamTournamentResponse(
    Long tournamentId,
    String status,
    int teamCount,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt,
    List<TournamentPlayerResponse> waitingPlayers,
    List<TournamentTeamResponse> teams,
    List<TournamentSeriesResponse> series
) {
}
