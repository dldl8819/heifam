package com.balancify.backend.service;

import com.balancify.backend.api.admin.dto.RatingRecalculationPlayerChangeResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

record RatingReplayPlan(
    List<PlayerResult> players,
    List<ParticipantResult> participants,
    int processedMatches,
    int updatedPlayers,
    double averageAbsoluteDeltaDifference,
    List<RatingRecalculationPlayerChangeResponse> samplePlayerChanges
) {

    Set<Long> playerIds() {
        return players.stream().map(PlayerResult::playerId).collect(java.util.stream.Collectors.toSet());
    }

    Set<Long> participantIds() {
        return participants.stream().map(ParticipantResult::participantId).collect(java.util.stream.Collectors.toSet());
    }

    Set<Long> matchIds() {
        return participants.stream().map(ParticipantResult::matchId).collect(java.util.stream.Collectors.toSet());
    }

    record PlayerResult(
        Long playerId,
        int finalMmr,
        String finalTier,
        int originalMmr
    ) {
    }

    record ParticipantResult(
        Long participantId,
        Long matchId,
        Long playerId,
        int beforeMmr,
        int afterMmr,
        int delta,
        OffsetDateTime historyCreatedAt
    ) {
    }
}
