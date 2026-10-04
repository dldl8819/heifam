package com.balancify.backend.api.prediction.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A match on the prediction board. state is OPEN (picks taken), CLOSED (waiting for the result)
 * or RESOLVED. Pick counts stay null while picks are taken, so nobody follows the crowd.
 */
public record PredictionMatchResponse(
    Long matchId,
    String state,
    String raceComposition,
    Integer seriesGameNumber,
    OffsetDateTime createdAt,
    OffsetDateTime closesAt,
    List<PredictionPlayerResponse> homePlayers,
    List<PredictionPlayerResponse> awayPlayers,
    String myPick,
    boolean ownMatch,
    Integer homePicks,
    Integer awayPicks,
    String winnerTeam,
    Boolean hit,
    boolean pointsExcluded
) {
}
