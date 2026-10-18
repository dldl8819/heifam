package com.balancify.backend.api.group.dto;

/**
 * Where a stage of a draft run by its captains stands (MAIN: the regular rounds, ACE: the ace
 * decider): which team has handed its line-up in, and whether both have, after which both teams'
 * players are shown to everyone.
 */
public record CaptainDraftLineupResponse(
    String stage,
    boolean homeSubmitted,
    boolean awaySubmitted,
    boolean revealed
) {
}
