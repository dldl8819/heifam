package com.balancify.backend.api.group.dto;

import java.util.List;

public record GroupPlayerImportResponse(
    int totalRows,
    int createdCount,
    int updatedCount,
    int failedCount,
    List<GroupPlayerImportFailedRowResponse> failedRows
) {
}
