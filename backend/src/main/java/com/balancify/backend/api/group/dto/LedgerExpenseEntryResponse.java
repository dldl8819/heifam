package com.balancify.backend.api.group.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LedgerExpenseEntryResponse(
    Long id,
    LocalDate entryDate,
    String expenseType,
    String category,
    String target,
    long amount,
    String memo,
    String authorNickname,
    OffsetDateTime createdAt
) {
}
