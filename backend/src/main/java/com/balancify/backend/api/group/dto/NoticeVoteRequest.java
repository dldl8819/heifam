package com.balancify.backend.api.group.dto;

public record NoticeVoteRequest(
    // AGREE or DISAGREE.
    String choice
) {
}
