package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One set: its team size (3 or 2; 1 for sets written before team games), the tiers it asks for,
 * and each side's players slot by slot, null where a slot is empty. With a result, who put it in
 * (the nickname as it was then, never the email) and when; null for results from before that.
 * homeHidden and awayHidden: a side kept from this reader, every slot null, because the line-ups of
 * its stage are not both in yet and the reader is not on that team. aceDecider: the set of the
 * extra round played when the regular rounds ended level.
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
    OffsetDateTime resultRecordedAt,
    boolean homeHidden,
    boolean awayHidden,
    boolean aceDecider
) {
}
