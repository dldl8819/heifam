package com.balancify.backend.api.group.dto;

import java.util.List;

public record LedgerImportResponse(
    int importedCount,
    List<LedgerImportRowError> skippedRows
) {
}
