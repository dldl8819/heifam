package com.balancify.backend.api.points.dto;

import java.util.List;

/**
 * How one account earned its points in a month: totals per reason, largest first, and the ledger
 * rows newest first (memos only for the account itself and super admins).
 */
public record PointMonthlyHistoryResponse(
    String month,
    Long accountId,
    String nickname,
    long points,
    List<PointReasonTotalResponse> reasons,
    List<PointHistoryItemResponse> entries
) {
}
