package com.balancify.backend.api.group.dto;

public record NoticeCommentRequest(
    String content,
    // The comment being answered; left out for a comment on the notice itself.
    Long parentId
) {
}
