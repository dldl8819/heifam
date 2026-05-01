package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record GroupPlayerUpdateRequest(
    String nickname,
    String race,
    Boolean active,
    OffsetDateTime chatLeftAt,
    String chatLeftReason,
    OffsetDateTime chatRejoinedAt,
    String tierChangeAcknowledgedTier
) {
}
