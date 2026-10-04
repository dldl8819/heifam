package com.balancify.backend.api.points.dto;

public record PrizeCandidateResponse(
    int rank,
    Long pointAccountId,
    String nickname,
    long points
) {
}
