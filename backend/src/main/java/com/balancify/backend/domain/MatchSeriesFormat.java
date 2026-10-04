package com.balancify.backend.domain;

/**
 * BEST_OF_THREE: three PPP games, over once a team has two wins.
 * MIXED_THREE: PPP, PPT and PPZ, all three played; a game either team cannot field becomes PPP.
 */
public enum MatchSeriesFormat {
    BEST_OF_THREE,
    MIXED_THREE
}
