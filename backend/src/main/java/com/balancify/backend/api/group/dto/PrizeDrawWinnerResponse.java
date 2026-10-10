package com.balancify.backend.api.group.dto;

public record PrizeDrawWinnerResponse(
    int place,
    String name,
    // Null when no prize was named.
    String prize
) {
}
