package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupPlayerResponse(
    Long id,
    String nickname,
    String race,
    String tier,
    Integer baseMmr,
    String baseTier,
    Integer currentMmr,
    int wins,
    int losses,
    int games,
    boolean active,
    OffsetDateTime chatLeftAt,
    String chatLeftReason,
    OffsetDateTime chatRejoinedAt
) {
}
