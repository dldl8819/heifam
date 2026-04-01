package com.balancify.backend.api.match.dto;

public record MultiBalancePenaltySummaryResponse(
    int repeatTeammatePenalty,
    int repeatMatchupPenalty,
    int racePenalty
) {
}

