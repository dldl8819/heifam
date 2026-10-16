package com.balancify.backend.api.group.dto;

/**
 * One set of a round. The host sends both teams' players (homePlayerId, awayPlayerId); a request
 * naming a captain sends that captain's player alone (playerId).
 */
public record CaptainDraftEntryUpdateItemRequest(
    Integer roundNumber,
    Integer setNumber,
    Long playerId,
    String winnerTeam,
    Long homePlayerId,
    Long awayPlayerId
) {
}
