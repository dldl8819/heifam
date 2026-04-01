package com.balancify.backend.api.group.dto;

public record DashboardMyRaceStatResponse(
    String race,
    int wins,
    int losses,
    int games,
    double winRate
) {
}
