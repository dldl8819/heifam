package com.balancify.backend.api.group.dto;

import java.util.List;

public record DashboardMyGameTypeSummaryResponse(
    boolean linked,
    String nickname,
    int wins,
    int losses,
    int games,
    double winRate,
    List<DashboardMyGameTypeStatResponse> byGameType
) {
}
