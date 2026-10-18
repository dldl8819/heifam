package com.balancify.backend.api.group.dto;

import java.util.List;

/** A captain's line-up for one round: every set of it, each with their team's players slot by slot. */
public record CaptainDraftLineupRequest(
    List<CaptainDraftLineupSetRequest> sets
) {
}
