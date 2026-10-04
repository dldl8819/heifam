package com.balancify.backend.api.tournament.dto;

import java.util.List;

public record CreateTeamTournamentRequest(
    List<Long> playerIds
) {
}
