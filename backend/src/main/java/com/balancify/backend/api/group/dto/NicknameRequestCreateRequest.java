package com.balancify.backend.api.group.dto;

public record NicknameRequestCreateRequest(
    String desiredNickname,
    // Optional.
    String reason
) {
}
