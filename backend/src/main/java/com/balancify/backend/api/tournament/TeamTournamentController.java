package com.balancify.backend.api.tournament;

import com.balancify.backend.api.tournament.dto.CreateTeamTournamentRequest;
import com.balancify.backend.api.tournament.dto.LatestTeamTournamentResponse;
import com.balancify.backend.api.tournament.dto.TeamTournamentResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.TeamTournamentService;
import com.balancify.backend.service.TournamentProgressService;
import com.balancify.backend.service.exception.MatchConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Team tournaments; games are recorded through the usual match result API. */
@RestController
@RequestMapping("/api/groups/{groupId}/tournaments")
public class TeamTournamentController {

    private final TeamTournamentService teamTournamentService;
    private final TournamentProgressService tournamentProgressService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;
    private final MmrAccessRequestResolver mmrAccessRequestResolver;

    public TeamTournamentController(
        TeamTournamentService teamTournamentService,
        TournamentProgressService tournamentProgressService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver,
        MmrAccessRequestResolver mmrAccessRequestResolver
    ) {
        this.teamTournamentService = teamTournamentService;
        this.tournamentProgressService = tournamentProgressService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
        this.mmrAccessRequestResolver = mmrAccessRequestResolver;
    }

    @PostMapping
    public TeamTournamentResponse create(
        @PathVariable Long groupId,
        @RequestBody CreateTeamTournamentRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRunner(request);
        if (requestBody == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return handle(() -> teamTournamentService.create(
            groupId,
            requestBody.playerIds(),
            requestEmail,
            resolveNickname(requestEmail),
            mmrAccessRequestResolver.canViewMmr(request)
        ));
    }

    @GetMapping("/latest")
    public LatestTeamTournamentResponse getLatest(@PathVariable Long groupId, HttpServletRequest request) {
        requireRunner(request);
        return new LatestTeamTournamentResponse(
            teamTournamentService.findLatest(groupId, mmrAccessRequestResolver.canViewMmr(request))
        );
    }

    @GetMapping("/{tournamentId}")
    public TeamTournamentResponse get(
        @PathVariable Long groupId,
        @PathVariable Long tournamentId,
        HttpServletRequest request
    ) {
        requireRunner(request);
        return handle(() -> teamTournamentService.get(
            groupId,
            tournamentId,
            mmrAccessRequestResolver.canViewMmr(request)
        ));
    }

    @PostMapping("/{tournamentId}/cancel")
    public TeamTournamentResponse cancel(
        @PathVariable Long groupId,
        @PathVariable Long tournamentId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRunner(request);
        return handle(() -> teamTournamentService.cancel(
            groupId,
            tournamentId,
            requestEmail,
            resolveNickname(requestEmail),
            mmrAccessRequestResolver.canViewMmr(request)
        ));
    }

    private String requireRunner(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        if (!tournamentProgressService.canRunTournaments(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team tournaments are not open to this account yet");
        }
        return requestEmail;
    }

    private String resolveNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }

    private <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (MatchConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
