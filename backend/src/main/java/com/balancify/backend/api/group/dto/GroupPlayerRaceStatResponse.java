package com.balancify.backend.api.group.dto;

public record GroupPlayerRaceStatResponse(
    String race,
    int wins,
    int losses,
    int games,
    double winRate
) {
}
