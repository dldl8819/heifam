package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One set: its team size (3 or 2; 1 for sets written before team games), the tiers it asks for,
 * and each side's players slot by slot, null where a slot is empty. With a result, who put it in
 * (the nickname as it was then, never the email) and when; null for results from before that.
 */
public record CaptainDraftEntryResponse(
    int roundNumber,
    String roundCode,
    int setNumber,
    int teamSize,
    String requiredTier,
    List<CaptainDraftEntryPlayerResponse> homePlayers,
    List<CaptainDraftEntryPlayerResponse> awayPlayers,
    String winnerTeam,
    String resultRecordedByNickname,
    OffsetDateTime resultRecordedAt
) {
}
