package com.balancify.backend.api.points.dto;

/** A candidate picked as a winner, in place order; amount 0 means no ledger expense. */
public record PrizeWinnerRequest(
    Long pointAccountId,
    String prize,
    Long amount
) {
}
