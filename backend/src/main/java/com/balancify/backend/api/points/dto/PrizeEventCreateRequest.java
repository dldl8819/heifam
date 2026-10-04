package com.balancify.backend.api.points.dto;

import java.time.LocalDate;

public record PrizeEventCreateRequest(
    String title,
    LocalDate periodStart,
    LocalDate periodEnd,
    Integer winnerCount
) {
}
