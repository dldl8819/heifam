package com.balancify.backend.api.group.dto;

public record LedgerMonthlySummaryItem(
    int month,
    long totalIncome,
    long totalFixedExpense,
    long totalVariableExpense,
    long totalExpense,
    long net,
    long cumulativeBalance
) {
}
