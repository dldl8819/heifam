package com.balancify.backend.api.group.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LedgerServerCostResponse(
    Long id,
    String serviceName,
    String billingMonth,
    LocalDate chargedDate,
    BigDecimal usdAmount,
    Long krwAmount,
    String paidBy,
    LocalDate reimbursedDate,
    String memo,
    String authorNickname,
    OffsetDateTime createdAt
) {
}
