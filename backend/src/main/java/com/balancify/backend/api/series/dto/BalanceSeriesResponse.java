package com.balancify.backend.api.series.dto;

import com.balancify.backend.api.tournament.dto.TournamentGameResponse;
import com.balancify.backend.api.tournament.dto.TournamentPlayerResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A series after a multi-balance. format is BEST_OF_THREE or MIXED_THREE and status IN_PROGRESS,
 * COMPLETED or CANCELLED; games read like a tournament series' games.
 */
public record BalanceSeriesResponse(
    Long seriesId,
    String status,
    String format,
    int teamSize,
    int homeWins,
    int awayWins,
    String winnerTeam,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt,
    List<TournamentPlayerResponse> homePlayers,
    List<TournamentPlayerResponse> awayPlayers,
    List<TournamentGameResponse> games
) {
}
