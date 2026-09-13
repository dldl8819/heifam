package com.balancify.backend.api.group.dto;

/**
 * Total spending in one expense category, fixed and variable entries combined.
 */
public record LedgerDashboardCategoryItem(
    String category,
    long amount,
    int count
) {
}
