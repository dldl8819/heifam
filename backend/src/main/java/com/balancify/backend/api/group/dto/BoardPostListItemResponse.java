package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record BoardPostListItemResponse(
    Long id,
    String title,
    // The YouTube video of a post on the video board, for its thumbnail; null on the other boards.
    String videoId,
    // Null on the anonymous board, for everyone.
    String authorNickname,
    OffsetDateTime createdAt,
    long commentCount,
    long likeCount,
    // People who have opened the post, each counted once.
    long viewCount,
    boolean mine
) {
}
