package com.balancify.backend.api.group.dto;

import java.util.List;

public record CreateGroupMatchRequest(
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds,
    String raceComposition
) {
    public CreateGroupMatchRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        this(homePlayerIds, awayPlayerIds, null);
    }
}
