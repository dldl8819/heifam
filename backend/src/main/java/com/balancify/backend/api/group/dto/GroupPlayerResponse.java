package com.balancify.backend.api.group.dto;

public record GroupPlayerResponse(
    Long id,
    String nickname,
    String race,
    String tier,
    int currentMmr,
    int wins,
    int losses,
    int games
) {
}

