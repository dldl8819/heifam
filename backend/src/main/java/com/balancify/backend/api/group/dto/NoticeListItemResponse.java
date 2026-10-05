package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record NoticeListItemResponse(
    Long id,
    String title,
    String authorNickname,
    OffsetDateTime createdAt,
    boolean adminOnly,
    boolean read,
    // An edit of this notice was announced again; with read false it explains why it is unread.
    boolean revised,
    long likeCount,
    long commentCount
) {
}
