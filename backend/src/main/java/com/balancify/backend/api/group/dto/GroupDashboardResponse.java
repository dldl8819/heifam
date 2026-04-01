package com.balancify.backend.api.group.dto;

import java.util.List;

public record GroupDashboardResponse(
    DashboardKpiSummaryResponse kpiSummary,
    List<DashboardTopRankingPreviewItemResponse> topRankingPreview,
    DashboardRecentBalancePreviewResponse recentBalancePreview,
    DashboardMyRaceSummaryResponse myRaceSummary,
    DashboardMyGameTypeSummaryResponse myGameTypeSummary
) {
}
