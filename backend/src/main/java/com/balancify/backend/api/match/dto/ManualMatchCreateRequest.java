package com.balancify.backend.api.match.dto;

import java.util.List;

public record ManualMatchCreateRequest(
    Long groupId,
    Integer teamSize,
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds,
    String winnerTeam,
    String note
) {
}
