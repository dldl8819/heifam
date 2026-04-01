package com.balancify.backend.api.match.dto;

import java.util.List;

public record MultiBalanceRequest(
    Long groupId,
    List<Long> playerIds,
    String balanceMode
) {
    public MultiBalanceRequest(Long groupId, List<Long> playerIds) {
        this(groupId, playerIds, null);
    }
}
