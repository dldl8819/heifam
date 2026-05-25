package com.balancify.backend.api.group.dto;

public record GroupPlayerTierBoardResponse(
    Long id,
    String nickname,
    String race,
    String tier,
    String liveTier,
    boolean active
) {
}
