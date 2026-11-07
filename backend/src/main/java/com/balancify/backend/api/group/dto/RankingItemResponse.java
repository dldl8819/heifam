package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingItemResponse(
    int rank,
    String nickname,
    String race,
    String tier,
    Integer currentMmr,
    int wins,
    int losses,
    int games,
    double winRate,
    String streak,
    String last10,
    Integer mmrDelta,
    boolean isNew,
    // The highest and lowest MMR the player has had: over their matches and now. Null with no match
    // yet, and for a reader who may not see MMR (MmrMaskingMapper).
    Integer peakMmr,
    Integer lowestMmr
) {
}
