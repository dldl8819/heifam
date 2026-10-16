package com.balancify.backend.api.points.dto;

import java.util.List;

public record PointSummaryResponse(
    long balance,
    int menuVisitsToday,
    int menuVisitDailyCap,
    int menuVisitPoints,
    // Which attendance menus paid today, by key; this summary is only ever the account's own.
    List<String> menusVisitedToday,
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
