package com.balancify.backend.api.group.dto;

import java.util.List;

public record CreateGroupMatchRequest(
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds,
    Integer teamSize,
    String raceComposition
) {
    public CreateGroupMatchRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        this(homePlayerIds, awayPlayerIds, null, null);
    }

    public CreateGroupMatchRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds, Integer teamSize) {
        this(homePlayerIds, awayPlayerIds, teamSize, null);
    }
}
