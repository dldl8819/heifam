package com.balancify.backend.api.group.dto;

public record CaptainDraftEntryUpdateItemRequest(
    Integer roundNumber,
    Integer setNumber,
    Long playerId,
    String winnerTeam
) {
}
