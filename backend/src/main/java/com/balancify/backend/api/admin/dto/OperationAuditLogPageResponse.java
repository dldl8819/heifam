package com.balancify.backend.api.admin.dto;

import java.util.List;

public record OperationAuditLogPageResponse(
    List<OperationAuditLogResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}
