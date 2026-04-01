package com.balancify.backend.api.group.dto;

public record CaptainDraftParticipantResponse(
    Long playerId,
    String nickname,
    String race,
    String team,
    boolean captain,
    Integer pickOrder
) {
}
