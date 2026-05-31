package com.balancify.backend.api.group.dto;

import java.util.List;

public record DashboardMyTeammateSummaryResponse(
    boolean linked,
    String nickname,
    int minGames,
    List<DashboardMyTeammateStatResponse> bestDuos,
    List<DashboardMyTeammateStatResponse> frequentTeammates,
    List<DashboardMyTeammateStatResponse> streakPartners
) {
}
