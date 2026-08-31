package com.balancify.backend.api.group.dto;

import java.util.List;

public record LedgerMonthlySummaryResponse(
    int year,
    List<LedgerMonthlySummaryItem> months
) {
}
