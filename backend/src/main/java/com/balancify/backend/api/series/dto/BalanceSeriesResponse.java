package com.balancify.backend.api.series.dto;

import com.balancify.backend.api.tournament.dto.TournamentGameResponse;
import com.balancify.backend.api.tournament.dto.TournamentPlayerResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A series after a multi-balance. matchNumber is its match in that multi-balance and the team
 * numbers are the ones it showed (null for series started before they were kept). format is
 * BEST_OF_THREE or MIXED_THREE and status IN_PROGRESS, COMPLETED or CANCELLED; games read like a
 * tournament series' games.
 */
public record BalanceSeriesResponse(
    Long seriesId,
    Integer matchNumber,
    Integer homeTeamNumber,
    Integer awayTeamNumber,
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
    List<TournamentGameResponse> games,
    // The nickname of whoever set up the game in play (the last game once the series is over), as
    // the predictions page names them; null when unknown.
    String createdByNickname
) {
}
