package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * One set: its team size (3 or 2; 1 for sets written before team games), the tiers it asks for,
 * and each side's players slot by slot, null where a slot is empty.
 */
public record CaptainDraftEntryResponse(
    int roundNumber,
    String roundCode,
    int setNumber,
    int teamSize,
    String requiredTier,
    List<CaptainDraftEntryPlayerResponse> homePlayers,
    List<CaptainDraftEntryPlayerResponse> awayPlayers,
    String winnerTeam
) {
}
