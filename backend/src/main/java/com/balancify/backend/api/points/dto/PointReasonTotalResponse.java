package com.balancify.backend.api.points.dto;

/** How many ledger rows of one reason a month holds, and what they add up to. */
public record PointReasonTotalResponse(String reason, int count, long points) {
}
