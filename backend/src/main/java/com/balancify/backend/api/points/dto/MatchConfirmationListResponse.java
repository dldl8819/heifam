package com.balancify.backend.api.points.dto;

import java.util.List;

public record MatchConfirmationListResponse(
    List<MatchConfirmationResponse> matches,
    int confirmedToday,
    int dailyCap,
    int points,
    int windowHours
) {
}
