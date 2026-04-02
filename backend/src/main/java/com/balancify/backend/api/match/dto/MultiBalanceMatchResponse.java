package com.balancify.backend.api.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MultiBalanceMatchResponse(
    int matchNumber,
    String matchType,
    int teamSize,
    List<BalancePlayerDto> homeTeam,
    List<BalancePlayerDto> awayTeam,
    Integer homeMmr,
    Integer awayMmr,
    Integer mmrDiff,
    Double expectedHomeWinRate,
    MultiBalanceRaceSummaryResponse raceSummary,
    MultiBalancePenaltySummaryResponse penaltySummary
) {
}
