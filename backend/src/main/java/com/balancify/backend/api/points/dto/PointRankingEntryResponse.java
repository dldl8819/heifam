package com.balancify.backend.api.points.dto;

public record PointRankingEntryResponse(
    int rank,
    String nickname,
    long points
) {
}
