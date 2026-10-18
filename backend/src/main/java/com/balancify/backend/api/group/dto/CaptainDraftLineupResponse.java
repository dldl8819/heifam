package com.balancify.backend.api.group.dto;

/**
 * Where a round of a draft run by its captains stands: which team has handed its line-up in, and
 * whether both have, after which both teams' players are shown to everyone.
 */
public record CaptainDraftLineupResponse(
    int roundNumber,
    boolean homeSubmitted,
    boolean awaySubmitted,
    boolean revealed
) {
}
