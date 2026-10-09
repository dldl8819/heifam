package com.balancify.backend.api.group.dto;

public record NoticeVoteRequest(
    // AGREE or DISAGREE: what a page loaded before votes had options sends for 찬성 and 반대.
    String choice,
    // The option voted for.
    Long optionId
) {
}
