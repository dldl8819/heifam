package com.balancify.backend.api.points.dto;

public record PrizeWinnerResponse(
    int place,
    String nickname,
    int points,
    String prize,
    long amount,
    boolean ledgerLinked
) {
}
