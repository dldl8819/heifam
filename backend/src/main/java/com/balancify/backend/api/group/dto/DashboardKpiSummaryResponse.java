package com.balancify.backend.api.group.dto;

public record DashboardKpiSummaryResponse(
    int totalPlayers,
    int topMmr,
    double averageMmr,
    int totalGames
) {
}

