package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record BoardSearchItemResponse(
    // NOTICE or FREE: where the post is, and so where its link leads.
    String kind,
    Long id,
    String title,
    // A stretch of the text around the first match, or its beginning when only the title matched.
    String snippet,
    String authorNickname,
    OffsetDateTime createdAt,
    // A notice kept to admins; only admins are sent one.
    boolean adminOnly,
    // People who have opened the post, each counted once.
    long viewCount,
    long commentCount,
    long likeCount,
    // People who have voted; null unless the post is a notice that asks for a vote.
    Long voteCount
) {
}
