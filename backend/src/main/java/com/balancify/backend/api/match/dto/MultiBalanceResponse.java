package com.balancify.backend.api.match.dto;

import java.util.List;

public record MultiBalanceResponse(
    String balanceMode,
    int totalPlayers,
    int assignedPlayers,
    List<MultiBalanceWaitingPlayerResponse> waitingPlayers,
    int matchCount,
    List<MultiBalanceMatchResponse> matches
) {
}
