package com.balancify.backend.api.admin.dto;

import java.util.List;

public record RatingRecalculationResponse(
    int processedMatches,
    int updatedPlayers,
    long durationMs,
    String status,
    boolean dryRun,
    double averageAbsoluteDeltaDifference,
    List<RatingRecalculationPlayerChangeResponse> samplePlayerChanges
) {
}
