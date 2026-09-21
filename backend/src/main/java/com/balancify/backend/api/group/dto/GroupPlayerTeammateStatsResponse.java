package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * How one player did alongside each of their teammates.
 *
 * <p>The totals cover only finished matches, the same ones the teammate rows are counted from, so
 * the numbers add up against each other.
 *
 * @param teammates highest win rate first, then most matches together
 */
public record GroupPlayerTeammateStatsResponse(
    Long playerId,
    String nickname,
    int wins,
    int losses,
    int games,
    double winRate,
    List<GroupPlayerTeammateStatResponse> teammates
) {
}
