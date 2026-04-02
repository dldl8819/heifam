package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingItemResponse(
    int rank,
    String nickname,
    String race,
    Integer currentMmr,
    int wins,
    int losses,
    int games,
    double winRate,
    String streak,
    String last10,
    Integer mmrDelta
) {
}
