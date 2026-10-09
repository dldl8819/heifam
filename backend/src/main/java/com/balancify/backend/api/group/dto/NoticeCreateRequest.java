package com.balancify.backend.api.group.dto;

public record NoticeCreateRequest(
    String title,
    String content,
    Boolean adminOnly,
    // OPEN asks members to vote for or against the notice; left out, it has no vote.
    String voteStatus
) {
}
