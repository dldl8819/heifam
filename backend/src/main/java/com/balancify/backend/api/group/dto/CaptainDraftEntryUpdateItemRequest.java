package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * One set as the host fills it in: each side's players slot by slot (null leaves a slot empty), the
 * tiers it asks for, and the winner.
 */
public record CaptainDraftEntryUpdateItemRequest(
    Integer roundNumber,
    Integer setNumber,
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds,
    String requiredTier,
    String winnerTeam
) {
}
