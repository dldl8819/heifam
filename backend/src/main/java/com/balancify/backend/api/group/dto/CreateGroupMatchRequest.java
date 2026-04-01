package com.balancify.backend.api.group.dto;

import java.util.List;

public record CreateGroupMatchRequest(
    List<Long> homePlayerIds,
    List<Long> awayPlayerIds
) {
}
