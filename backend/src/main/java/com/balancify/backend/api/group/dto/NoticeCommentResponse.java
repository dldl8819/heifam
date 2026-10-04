package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record NoticeCommentResponse(
    Long id,
    String authorNickname,
    String content,
    OffsetDateTime createdAt,
    boolean mine,
    boolean canDelete
) {
}
