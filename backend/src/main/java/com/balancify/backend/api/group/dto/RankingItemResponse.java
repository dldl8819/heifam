package com.balancify.backend.api.group.dto;

public record RankingItemResponse(
    int rank,
    String nickname,
    String race,
    int currentMmr,
    int wins,
    int losses,
    int games,
    double winRate,
    String streak,
    String last10,
    int mmrDelta
) {
}

