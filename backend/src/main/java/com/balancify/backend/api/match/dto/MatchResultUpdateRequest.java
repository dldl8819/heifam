package com.balancify.backend.api.match.dto;

import java.util.List;

public record MatchResultUpdateRequest(
    String winnerTeam,
    String raceComposition,
    List<ParticipantRaceRequest> participantRaces
) {
    public MatchResultUpdateRequest(String winnerTeam, String raceComposition) {
        this(winnerTeam, raceComposition, null);
    }
}
