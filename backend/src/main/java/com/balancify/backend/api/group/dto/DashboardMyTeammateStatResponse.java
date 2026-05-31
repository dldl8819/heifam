package com.balancify.backend.api.group.dto;

public record DashboardMyTeammateStatResponse(
    String nickname,
    int wins,
    int losses,
    int games,
    double winRate,
    int currentWinStreak
) {
}
