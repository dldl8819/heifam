package com.balancify.backend.api.group.dto;

import java.time.LocalDate;

/**
 * The account balance at the end of a day that had ledger entries.
 *
 * @param change net amount of that day's entries (income minus expense)
 * @param balance balance after that day's entries
 */
public record LedgerDashboardBalancePoint(
    LocalDate date,
    long change,
    long balance
) {
}
