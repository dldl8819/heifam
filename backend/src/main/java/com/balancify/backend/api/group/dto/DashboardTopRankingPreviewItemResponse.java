package com.balancify.backend.api.group.dto;

public record DashboardTopRankingPreviewItemResponse(
    int rank,
    String nickname,
    String race,
    int currentMmr,
    double winRate
) {
}

