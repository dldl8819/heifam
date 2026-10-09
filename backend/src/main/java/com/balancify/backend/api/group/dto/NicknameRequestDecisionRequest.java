package com.balancify.backend.api.group.dto;

public record NicknameRequestDecisionRequest(
    // APPROVED or REJECTED.
    String status,
    // Optional; shown to whoever asked.
    String note
) {
}
