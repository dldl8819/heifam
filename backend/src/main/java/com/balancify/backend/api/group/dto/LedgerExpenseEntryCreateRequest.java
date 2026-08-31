package com.balancify.backend.api.group.dto;

import java.time.LocalDate;

public record LedgerExpenseEntryCreateRequest(
    LocalDate entryDate,
    String expenseType,
    String category,
    String target,
    Long amount,
    String memo
) {
}
