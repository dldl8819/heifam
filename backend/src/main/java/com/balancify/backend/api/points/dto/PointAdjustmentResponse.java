package com.balancify.backend.api.points.dto;

public record PointAdjustmentResponse(
    String nickname,
    int amount,
    long balance
) {
}
