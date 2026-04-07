package com.balancify.backend.api.match.dto;

import java.util.List;

public record MultiBalanceRequest(
    Long groupId,
    List<Long> playerIds,
    String balanceMode,
    String raceComposition
) {
    public MultiBalanceRequest(Long groupId, List<Long> playerIds) {
        this(groupId, playerIds, null, null);
    }

    public MultiBalanceRequest(Long groupId, List<Long> playerIds, String balanceMode) {
        this(groupId, playerIds, balanceMode, null);
    }
}
