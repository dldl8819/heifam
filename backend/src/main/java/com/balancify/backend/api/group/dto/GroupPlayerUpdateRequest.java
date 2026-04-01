package com.balancify.backend.api.group.dto;

public record GroupPlayerUpdateRequest(
    String nickname,
    String race
) {
}
