package com.balancify.backend.api.prediction.dto;

public record PredictionStatsResponse(
    long resolved,
    long hits
) {
}
