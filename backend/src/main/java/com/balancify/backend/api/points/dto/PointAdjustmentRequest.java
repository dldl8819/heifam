package com.balancify.backend.api.points.dto;

public record PointAdjustmentRequest(
    String email,
    Integer amount,
    String memo
) {
}
