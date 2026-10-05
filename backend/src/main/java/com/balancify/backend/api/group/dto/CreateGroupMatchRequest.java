package com.balancify.backend.api.group.dto;

import java.util.List;

public record CreateGroupMatchRequest(
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds,
    Integer teamSize,
    String raceComposition,
    // The result is entered right after this call, so nobody could predict: no predictions are announced.
    Boolean resultFollows
) {
    public CreateGroupMatchRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        this(homePlayerIds, awayPlayerIds, null, null, null);
    }

    public CreateGroupMatchRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds, Integer teamSize) {
        this(homePlayerIds, awayPlayerIds, teamSize, null, null);
    }

    public CreateGroupMatchRequest(
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        Integer teamSize,
        String raceComposition
    ) {
        this(homePlayerIds, awayPlayerIds, teamSize, raceComposition, null);
    }
}
