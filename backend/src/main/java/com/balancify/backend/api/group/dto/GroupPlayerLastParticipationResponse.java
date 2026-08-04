package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GroupPlayerLastParticipationResponse(
    OffsetDateTime lastPlayedAt
) {
}
