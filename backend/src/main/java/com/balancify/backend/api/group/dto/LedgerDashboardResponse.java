package com.balancify.backend.api.group.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * All-time view of the donation account for the ledger dashboard.
 *
 * <p>It carries amounts and categories only - no donor memo, payment target or author - so it can
 * later be shown to regular members without exposing who gave or received what.
 *
 * @param asOfDate date of the latest counted entry, or null when the ledger is empty
 * @param startingBalanceDate date of the earliest "기초 잔액" entry, or null when there is none
 * @param totalIncome income counted from the starting balance date, excluding the starting balance
 * @param currentBalance {@code startingBalance + totalIncome - totalExpense}
 */
public record LedgerDashboardResponse(
    LocalDate asOfDate,
    LocalDate startingBalanceDate,
    long startingBalance,
    long totalIncome,
    int incomeCount,
    long totalFixedExpense,
    long totalVariableExpense,
    long totalExpense,
    int expenseCount,
    long currentBalance,
    List<LedgerDashboardBalancePoint> balanceTimeline,
    List<LedgerDashboardMonthItem> months,
    List<LedgerDashboardCategoryItem> expenseCategories
) {
}
