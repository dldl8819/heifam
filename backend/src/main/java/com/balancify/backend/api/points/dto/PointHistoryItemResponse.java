package com.balancify.backend.api.points.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PointHistoryItemResponse(
    String reason,
    int amount,
    LocalDate kstDate,
    String memo,
    OffsetDateTime createdAt
) {
}
