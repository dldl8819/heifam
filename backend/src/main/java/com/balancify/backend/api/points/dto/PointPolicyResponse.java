package com.balancify.backend.api.points.dto;

/** What each activity earns and how often a day, as configured; the points policy page shows it. */
public record PointPolicyResponse(
    int dailyLogin,
    Capped matchResult,
    Capped matchConfirm,
    int matchConfirmWindowHours,
    Capped predictionHit,
    int noticeAction,
    Capped noticeCommentLike,
    Capped boardPost,
    Capped boardComment,
    Capped boardLike
) {

    /** Points per time, and the most times a day (for predictions, the most points a day). */
    public record Capped(int points, int dailyCap) {
    }
}
