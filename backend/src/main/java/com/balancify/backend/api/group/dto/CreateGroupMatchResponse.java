package com.balancify.backend.api.group.dto;

public record CreateGroupMatchResponse(
    Long matchId,
    String confirmationStatus,
    String message
) {
}
