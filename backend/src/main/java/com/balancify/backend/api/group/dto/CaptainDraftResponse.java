package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * createdByNickname: who started the draft, as their nickname was then (never the email).
 * entryMode: HOST (the host fills both teams in) or CAPTAINS (each captain hands their team in a
 * round at a time). viewerTeam and viewerCaptain: the team of the player whose account asks, and
 * whether they captain it; null and false for anyone not in the draft. lineups: each round's
 * line-ups handed in, for a draft run by its captains (empty otherwise).
 */
public record CaptainDraftResponse(
    Long draftId,
    Long groupId,
    String title,
    OffsetDateTime createdAt,
    String createdByNickname,
    String status,
    int setsPerRound,
    int participantCount,
    String currentTurnTeam,
    Long homeCaptainPlayerId,
    String homeCaptainNickname,
    Long awayCaptainPlayerId,
    String awayCaptainNickname,
    List<CaptainDraftParticipantResponse> participants,
    List<CaptainDraftPickLogResponse> picks,
    List<CaptainDraftEntryResponse> entries,
    String entryMode,
    String viewerTeam,
    boolean viewerCaptain,
    List<CaptainDraftLineupResponse> lineups
) {
}
