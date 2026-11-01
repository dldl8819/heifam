package com.balancify.backend.api.points.dto;

import java.time.LocalDate;
import java.util.List;

public record PrizeEventCreateRequest(
    String title,
    LocalDate periodStart,
    LocalDate periodEnd,
    Integer winnerCount,
    // POINTS (default), GAMES or MANUAL.
    String metric,
    // MANUAL only: what the numbers count, and each player's number as counted outside the site.
    String unitLabel,
    List<PrizeEventEntryRequest> entries
) {
}
