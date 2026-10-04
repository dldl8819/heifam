package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoticeDetailResponse(
    Long id,
    String title,
    String content,
    String authorNickname,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean adminOnly,
    long likeCount,
    boolean likedByMe,
    List<NoticeCommentResponse> comments
) {
}
