package com.balancify.backend.api.admin.dto;

public record RatingRecalculationPlayerChangeResponse(
    Long playerId,
    String nickname,
    Integer beforeMmr,
    Integer afterMmr
) {
}
