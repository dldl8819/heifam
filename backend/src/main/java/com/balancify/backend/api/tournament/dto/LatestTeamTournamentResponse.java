package com.balancify.backend.api.tournament.dto;

/** The group's latest tournament that was not cancelled, or null when there is none. */
public record LatestTeamTournamentResponse(
    TeamTournamentResponse tournament
) {
}
