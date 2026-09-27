package com.balancify.backend.api.match.dto;

import java.util.List;

public record MatchResultRequest(
    String winnerTeam,
    List<ParticipantRaceRequest> participantRaces
) {
    public MatchResultRequest(String winnerTeam) {
        this(winnerTeam, null);
    }
}
