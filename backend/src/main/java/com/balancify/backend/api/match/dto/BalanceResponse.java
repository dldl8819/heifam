package com.balancify.backend.api.match.dto;

import java.util.List;

public record BalanceResponse(
    int teamSize,
    List<BalancePlayerDto> homeTeam,
    List<BalancePlayerDto> awayTeam,
    int homeMmr,
    int awayMmr,
    int mmrDiff,
    double expectedHomeWinRate
) {
}
