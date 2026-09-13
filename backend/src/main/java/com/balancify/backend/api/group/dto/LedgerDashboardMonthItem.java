package com.balancify.backend.api.group.dto;

/**
 * Totals for one calendar month of the ledger dashboard.
 *
 * @param month year and month as {@code yyyy-MM}
 * @param income income for the month, excluding the starting balance
 * @param net {@code income - totalExpense}
 * @param endBalance account balance at the end of the month, starting balance included
 */
public record LedgerDashboardMonthItem(
    String month,
    long income,
    int incomeCount,
    long fixedExpense,
    long variableExpense,
    long totalExpense,
    int expenseCount,
    long net,
    long endBalance
) {
}
