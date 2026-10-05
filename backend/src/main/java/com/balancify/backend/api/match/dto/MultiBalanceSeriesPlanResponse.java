package com.balancify.backend.api.match.dto;

import java.util.List;

/**
 * How the two teams of a multi-balance match would play their series: MIXED_THREE is PPP, PPT and
 * PPZ (PP, PT and PZ for two players), BEST_OF_THREE the all-Protoss game. Either way the series
 * is over once a team has two wins, so the third game is played only at 1:1.
 */
public record MultiBalanceSeriesPlanResponse(String format, List<MultiBalanceSeriesGameResponse> games) {
}
