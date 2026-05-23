package com.balancify.backend.api.admin.dto;

import java.time.OffsetDateTime;

public record OperationAuditLogResponse(
    Long id,
    String action,
    String actorEmail,
    String actorNickname,
    String targetType,
    Long targetId,
    String targetLabel,
    Long groupId,
    String summary,
    String details,
    OffsetDateTime createdAt
) {
}
