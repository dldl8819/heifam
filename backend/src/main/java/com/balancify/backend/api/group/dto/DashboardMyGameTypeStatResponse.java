package com.balancify.backend.api.group.dto;

public record DashboardMyGameTypeStatResponse(
    String gameType,
    int wins,
    int losses,
    int games,
    double winRate
) {
}
