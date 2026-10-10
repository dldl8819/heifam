package com.balancify.backend.api.group.dto;

public record PrizeDrawWinnerRequest(
    // 1 for first place, and so on without a gap.
    Integer place,
    String name,
    // The roster player the name belongs to; left out for a name typed by hand.
    Long playerId,
    // Optional.
    String prize
) {
}
