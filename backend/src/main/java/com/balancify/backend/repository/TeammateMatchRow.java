package com.balancify.backend.repository;

/**
 * One player's side in a match, as a teammate record reads it: the match, the team they were on,
 * the team that won and who they are. Read whole instead (every column of the match and of the
 * player, on every row), a busy player's matches came to megabytes a lookup.
 */
public record TeammateMatchRow(
    Long matchId,
    String team,
    String winningTeam,
    Long playerId
) {
}
