package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * A captain's line-up, handed in once: stage MAIN (the default) takes every set of every regular
 * round, ACE every set of the ace decider, each with their team's players slot by slot.
 */
public record CaptainDraftLineupRequest(
    String stage,
    List<CaptainDraftLineupSetRequest> sets
) {
}
