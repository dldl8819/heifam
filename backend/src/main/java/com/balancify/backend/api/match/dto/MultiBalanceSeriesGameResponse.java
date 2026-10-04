package com.balancify.backend.api.match.dto;

import java.util.List;

/** One planned game; the races follow the order of the match's homeTeam and awayTeam. */
public record MultiBalanceSeriesGameResponse(
    int gameNumber,
    String raceComposition,
    List<String> homeRaces,
    List<String> awayRaces
) {
}
