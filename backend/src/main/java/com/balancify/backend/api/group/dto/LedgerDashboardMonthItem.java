package com.balancify.backend.api.group.dto;

/**
 * Totals for one calendar month of the ledger dashboard.
 *
 * @param month year and month as {@code yyyy-MM}
 * @param income income for the month, excluding the starting balance
 * @param serverCostReimbursed server costs paid back from the account during the month
 * @param net {@code income - totalExpense - serverCostReimbursed}
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
    long serverCostReimbursed,
    long net,
    long endBalance
) {
}
