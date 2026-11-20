package com.balancify.backend.api.group.dto;

public record PrizeDrawWinnerResponse(
    int place,
    String name,
    // The roster player the winner was picked as; null for a name typed by hand, or once the member
    // left. A later draw leaves past winners out by it (the setup on /draws).
    Long playerId,
    // Null when no prize was named.
    String prize
) {
}
