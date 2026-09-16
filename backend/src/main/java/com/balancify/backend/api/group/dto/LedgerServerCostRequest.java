package com.balancify.backend.api.group.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create or update payload for a server cost.
 *
 * @param billingMonth billing period as {@code yyyy-MM}
 * @param usdAmount invoice amount in US dollars, if the provider bills in dollars
 * @param krwAmount amount charged to the card in won, once known
 * @param reimbursedDate when the donation account paid the cost back; requires {@code krwAmount}
 */
public record LedgerServerCostRequest(
    String serviceName,
    String billingMonth,
    LocalDate chargedDate,
    BigDecimal usdAmount,
    Long krwAmount,
    String paidBy,
    LocalDate reimbursedDate,
    String memo
) {
}
