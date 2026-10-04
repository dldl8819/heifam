package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record NoticeListItemResponse(
    Long id,
    String title,
    String authorNickname,
    OffsetDateTime createdAt,
    boolean adminOnly,
    boolean read,
    long likeCount,
    long commentCount
) {
}
