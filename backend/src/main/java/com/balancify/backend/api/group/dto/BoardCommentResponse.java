package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record BoardCommentResponse(
    Long id,
    // Null on the anonymous board when the comment is by whoever wrote the post.
    String authorNickname,
    boolean byPostAuthor,
    String content,
    OffsetDateTime createdAt,
    boolean mine,
    boolean canDelete
) {
}
