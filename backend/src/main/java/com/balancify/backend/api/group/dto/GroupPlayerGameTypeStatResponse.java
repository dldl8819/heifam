package com.balancify.backend.api.group.dto;

public record GroupPlayerGameTypeStatResponse(
    String gameType,
    int wins,
    int losses,
    int games,
    double winRate
) {
}
