package com.balancify.backend.api.group.dto;

import java.util.List;

public record CaptainDraftCreateRequest(
    String title,
    List<Long> participantPlayerIds,
    List<Long> captainPlayerIds,
    Integer setsPerRound
) {
}
