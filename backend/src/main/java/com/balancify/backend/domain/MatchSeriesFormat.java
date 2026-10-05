package com.balancify.backend.domain;

/**
 * BEST_OF_THREE: three PPP games, over once a team has two wins.
 * MIXED_THREE: PPP, PPT and PPZ; a game either team cannot field becomes PPP. A tournament series
 * plays all three, a multi-balance series stops at two wins like a best of three.
 */
public enum MatchSeriesFormat {
    BEST_OF_THREE,
    MIXED_THREE
}
