package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardRecentBalancePreviewResponse(
    Long matchId,
    List<DashboardRecentBalanceTeamPlayerResponse> homeTeam,
    List<DashboardRecentBalanceTeamPlayerResponse> awayTeam,
    int homeMmr,
    int awayMmr,
    int mmrDiff,
    OffsetDateTime createdAt
) {
}

