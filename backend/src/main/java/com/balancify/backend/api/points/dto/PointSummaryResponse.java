package com.balancify.backend.api.points.dto;

import java.util.List;

public record PointSummaryResponse(
    long balance,
    boolean dailyLoginEarnedToday,
    int dailyLoginPoints,
    int matchResultsToday,
    int matchResultDailyCap,
    int matchResultPoints,
    List<PointHistoryItemResponse> recent
) {
}
