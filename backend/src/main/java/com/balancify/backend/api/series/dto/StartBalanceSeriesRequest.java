package com.balancify.backend.api.series.dto;

import java.util.List;

/** The matches of a multi-balance to play as series, one lineup per match. */
public record StartBalanceSeriesRequest(List<BalanceSeriesLineupRequest> lineups) {
}
