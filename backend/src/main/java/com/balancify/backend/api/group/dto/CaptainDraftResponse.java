package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

// createdByNickname: who started the draft, as their nickname was then (never the email).
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
    List<CaptainDraftEntryResponse> entries
) {
}
