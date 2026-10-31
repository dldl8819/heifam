package com.balancify.backend.api.points.dto;

/**
 * A candidate picked as a winner, in place order; amount 0 means no ledger expense. A points event's
 * candidate is named by pointAccountId, a matches-played event's by playerId.
 */
public record PrizeWinnerRequest(
    Long pointAccountId,
    Long playerId,
    String prize,
    Long amount
) {
}
