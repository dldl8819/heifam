package com.balancify.backend.api.group.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LedgerIncomeEntryResponse(
    Long id,
    LocalDate entryDate,
    String category,
    long amount,
    String memo,
    String authorNickname,
    OffsetDateTime createdAt
) {
}
