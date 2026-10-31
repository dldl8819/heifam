package com.balancify.backend.api.points.dto;

public record PrizeCandidateResponse(
    int rank,
    Long pointAccountId,
    String nickname,
    // Points earned, or matches played in a GAMES event.
    long points,
    // A GAMES event's candidate is a player (pointAccountId null); a POINTS one's is an account.
    Long playerId
) {
}
