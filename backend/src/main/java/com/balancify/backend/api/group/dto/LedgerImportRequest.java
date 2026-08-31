package com.balancify.backend.api.group.dto;

public record LedgerImportRequest(
    String csvContent,
    String expenseType
) {
}
