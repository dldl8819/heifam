package com.balancify.backend.domain;

/**
 * Decides which matches move player ratings.
 *
 * <p>Only full 3v3 games are rated. 2v2 games are arranged and recorded the same way — they appear
 * in the match history and in a player's win/loss record — but they leave MMR and tier untouched,
 * because a two-player team makes an individual result far noisier than the rating system assumes.
 *
 * <p>Both the live result path and the rating replay used by a full recalculation consult this, so
 * a recalculation reproduces exactly what the live path recorded.
 */
public final class RankedMatchPolicy {

    private static final int RATED_TEAM_SIZE = 3;

    private RankedMatchPolicy() {
    }

    /**
     * @param teamSize players per team, as resolved for the match
     * @return whether a result for this match should change player ratings
     */
    public static boolean affectsRating(Integer teamSize) {
        return teamSize != null && teamSize == RATED_TEAM_SIZE;
    }
}
