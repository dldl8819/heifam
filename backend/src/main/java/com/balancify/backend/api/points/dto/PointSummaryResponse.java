package com.balancify.backend.api.points.dto;

import java.util.List;

public record PointSummaryResponse(
    long balance,
    boolean dailyLoginEarnedToday,
    int dailyLoginPoints,
    int matchResultsToday,
    int matchResultDailyCap,
    int matchResultPoints,
    int predictionPointsToday,
    int predictionHitDailyCap,
    int predictionHitPoints,
    int matchConfirmsToday,
    int matchConfirmDailyCap,
    int matchConfirmPoints,
    int matchConfirmWindowHours,
    int noticeCommentLikesToday,
    int noticeCommentLikeDailyCap,
    int noticeCommentLikePoints,
    List<PointHistoryItemResponse> recent
) {
}
