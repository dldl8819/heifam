package com.balancify.backend.api.match.dto;

public record ParticipantRaceRequest(
    Long playerId,
    String race
) {
}
