package com.balancify.backend.api.match.dto;

import java.util.List;

public record MultiBalanceMatchResponse(
    int matchNumber,
    String matchType,
    int teamSize,
    List<BalancePlayerDto> homeTeam,
    List<BalancePlayerDto> awayTeam,
    int homeMmr,
    int awayMmr,
    int mmrDiff,
    double expectedHomeWinRate,
    MultiBalanceRaceSummaryResponse raceSummary,
    MultiBalancePenaltySummaryResponse penaltySummary
) {
}
