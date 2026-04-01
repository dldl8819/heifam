package com.balancify.backend.api.group.dto;

import java.util.List;

public record DashboardMyRaceSummaryResponse(
    boolean linked,
    String nickname,
    int wins,
    int losses,
    int games,
    double winRate,
    List<DashboardMyRaceStatResponse> byRace
) {
}
