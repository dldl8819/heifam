package com.balancify.backend.api.match.dto;

import java.util.List;

public record MatchImportResponse(
    int totalRows,
    int importedCount,
    int failedCount,
    List<MatchImportFailedRowResponse> failedRows
) {
}

