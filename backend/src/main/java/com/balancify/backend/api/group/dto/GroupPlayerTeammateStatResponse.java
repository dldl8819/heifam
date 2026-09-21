package com.balancify.backend.api.group.dto;

/**
 * One player's record in the matches they played on the same team as the requested player.
 *
 * @param winRate percentage with two decimals, e.g. 66.67
 * @param currentWinStreak wins together in a row, counting back from their latest shared match
 */
public record GroupPlayerTeammateStatResponse(
    Long playerId,
    String nickname,
    int wins,
    int losses,
    int games,
    double winRate,
    int currentWinStreak
) {
}
