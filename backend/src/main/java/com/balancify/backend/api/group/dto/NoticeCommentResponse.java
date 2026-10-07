package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record NoticeCommentResponse(
    Long id,
    // The comment this one answers; null for a comment on the notice itself.
    Long parentId,
    String authorNickname,
    String content,
    OffsetDateTime createdAt,
    boolean edited,
    // Deleted while it had replies: an emptied place that only holds its replies together.
    boolean deleted,
    long likeCount,
    boolean likedByMe,
    boolean mine,
    boolean canDelete
) {
}
