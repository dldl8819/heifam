package com.balancify.backend.api.group.dto;

import java.util.List;

public record GroupPlayerRaceStatsResponse(
    Long playerId,
    String nickname,
    String race,
    int wins,
    int losses,
    int games,
    double winRate,
    List<GroupPlayerRaceStatResponse> byRace,
    List<GroupPlayerGameTypeStatResponse> byGameType
) {
}
