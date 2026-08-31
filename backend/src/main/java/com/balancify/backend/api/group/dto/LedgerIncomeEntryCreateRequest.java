package com.balancify.backend.api.group.dto;

import java.time.LocalDate;

public record LedgerIncomeEntryCreateRequest(
    LocalDate entryDate,
    String category,
    Long amount,
    String memo
) {
}
