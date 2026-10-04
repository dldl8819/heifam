package com.balancify.backend.api.tournament;

import com.balancify.backend.api.tournament.dto.TeamScoreBoardResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.TeamScoreService;
import com.balancify.backend.service.TournamentProgressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Team scores come from team tournaments, so they open to members together with them. */
@RestController
public class TeamScoreController {

    private final TeamScoreService teamScoreService;
    private final TournamentProgressService tournamentProgressService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public TeamScoreController(
        TeamScoreService teamScoreService,
        TournamentProgressService tournamentProgressService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.teamScoreService = teamScoreService;
        this.tournamentProgressService = tournamentProgressService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/api/groups/{groupId}/team-scores")
    public TeamScoreBoardResponse getTeamScores(@PathVariable Long groupId, HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        if (!tournamentProgressService.canRunTournaments(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team scores are not open to this account yet");
        }
        return teamScoreService.board(groupId);
    }
}
