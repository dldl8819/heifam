package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CaptainDraftResponse(
    Long draftId,
    Long groupId,
    String title,
    OffsetDateTime createdAt,
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
