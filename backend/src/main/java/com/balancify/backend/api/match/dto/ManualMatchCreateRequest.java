package com.balancify.backend.api.match.dto;

import java.util.List;

public record ManualMatchCreateRequest(
    Long groupId,
    Integer teamSize,
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds,
    String winnerTeam,
    String note,
    String raceComposition,
    List<ParticipantRaceRequest> participantRaces
) {
    public ManualMatchCreateRequest(
        Long groupId,
        Integer teamSize,
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        String winnerTeam,
        String note,
        String raceComposition
    ) {
        this(groupId, teamSize, homePlayerIds, awayPlayerIds, winnerTeam, note, raceComposition, null);
    }

    public ManualMatchCreateRequest(
        Long groupId,
        Integer teamSize,
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        String winnerTeam,
        String note
    ) {
        this(groupId, teamSize, homePlayerIds, awayPlayerIds, winnerTeam, note, null, null);
    }
}
