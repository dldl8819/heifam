package com.balancify.backend.api.points.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A point row. memo and createdAt are null on another member's row: they see the day, not the time. */
public record PointHistoryItemResponse(
    String reason,
    int amount,
    LocalDate kstDate,
    String memo,
    OffsetDateTime createdAt
) {
}
