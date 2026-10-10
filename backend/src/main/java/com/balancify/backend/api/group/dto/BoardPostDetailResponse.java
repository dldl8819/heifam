package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BoardPostDetailResponse(
    Long id,
    // FREE, ANONYMOUS or VIDEO.
    String board,
    String title,
    String content,
    // The YouTube video of a post on the video board, played on its page; null on the other boards.
    String videoId,
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
