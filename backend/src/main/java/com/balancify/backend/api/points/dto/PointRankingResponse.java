package com.balancify.backend.api.points.dto;

import java.util.List;

public record PointRankingResponse(
    String month,
    List<PointRankingEntryResponse> entries
) {
}
