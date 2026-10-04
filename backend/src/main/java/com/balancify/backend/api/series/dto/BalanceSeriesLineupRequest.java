package com.balancify.backend.api.series.dto;

import java.util.List;

/**
 * One match to play as a series. format is MIXED_THREE or BEST_OF_THREE to choose how teams that
 * could mix play; left out, they mix whenever both can field a Terran or Zerg game.
 */
public record BalanceSeriesLineupRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds, String format) {

    public BalanceSeriesLineupRequest(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        this(homePlayerIds, awayPlayerIds, null);
    }
}
