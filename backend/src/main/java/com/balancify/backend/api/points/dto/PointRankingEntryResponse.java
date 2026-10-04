package com.balancify.backend.api.points.dto;

/** accountId opens the entry's monthly history; emails stay on the server. */
public record PointRankingEntryResponse(
    int rank,
    Long accountId,
    String nickname,
    long points
) {
}
