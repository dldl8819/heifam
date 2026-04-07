package com.balancify.backend.api.match.dto;

import java.util.List;

public record BalanceRequest(
    Long groupId,
    List<Long> playerIds,
    Integer teamSize,
    List<BalancePlayerDto> players,
    String raceComposition
) {
    public BalanceRequest(List<BalancePlayerDto> players) {
        this(null, null, null, players, null);
    }

    public BalanceRequest(Long groupId, List<Long> playerIds, Integer teamSize) {
        this(groupId, playerIds, teamSize, null, null);
    }
}
