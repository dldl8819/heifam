package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record NoticeResponse(
    Long id,
    String title,
    String content,
    String authorNickname,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
