package com.balancify.backend.api.match.dto;

import java.util.List;

public record BalanceRequest(
    Long groupId,
    List<Long> playerIds,
    Integer teamSize,
    List<BalancePlayerDto> players
) {
    public BalanceRequest(List<BalancePlayerDto> players) {
        this(null, null, null, players);
    }

    public BalanceRequest(Long groupId, List<Long> playerIds, Integer teamSize) {
        this(groupId, playerIds, teamSize, null);
    }
}
