package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record NicknameRequestResponse(
    Long id,
    // The nickname the account showed when it asked; null when it had none.
    String currentNickname,
    String desiredNickname,
    String reason,
    // PENDING, APPROVED, REJECTED or CANCELED.
    String status,
    String adminNote,
    // The admin who decided it; null while it waits, and for an admin without a nickname.
    String processedByNickname,
    OffsetDateTime processedAt,
    OffsetDateTime createdAt,
    boolean mine,
    boolean canCancel,
    boolean canDecide
) {
}
