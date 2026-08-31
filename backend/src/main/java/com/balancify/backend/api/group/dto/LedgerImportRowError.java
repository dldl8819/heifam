package com.balancify.backend.api.group.dto;

public record LedgerImportRowError(
    int rowNumber,
    String reason
) {
}
