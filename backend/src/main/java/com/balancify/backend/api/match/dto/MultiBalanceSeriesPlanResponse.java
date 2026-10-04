package com.balancify.backend.api.match.dto;

import java.util.List;

/**
 * How the two teams of a multi-balance match would play their series: MIXED_THREE is PPP, PPT and
 * PPZ (PP, PT and PZ for two players) with all three played, BEST_OF_THREE the all-Protoss game
 * until a team has two wins.
 */
public record MultiBalanceSeriesPlanResponse(String format, List<MultiBalanceSeriesGameResponse> games) {
}
