package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BoardPostDetailResponse(
    Long id,
    // FREE or ANONYMOUS.
    String board,
    String title,
    String content,
    // Null on the anonymous board, for everyone; the writer is told by "mine" instead.
    String authorNickname,
    OffsetDateTime createdAt,
    boolean edited,
    boolean mine,
    boolean canEdit,
    boolean canDelete,
    long likeCount,
    boolean likedByMe,
    long viewCount,
    List<BoardCommentResponse> comments
) {
}
