package com.balancify.backend.api.points;

import com.balancify.backend.api.points.dto.MatchConfirmationListResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.MatchConfirmationService;
import com.balancify.backend.service.PointService;
import com.balancify.backend.service.exception.MatchConfirmationForbiddenException;
import com.balancify.backend.service.exception.MatchConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Players confirm the results of matches they played, a point each; follows the points flag. */
@RestController
@RequestMapping("/api/groups/{groupId}/match-confirmations")
public class MatchConfirmationController {

    private final MatchConfirmationService matchConfirmationService;
    private final PointService pointService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public MatchConfirmationController(
        MatchConfirmationService matchConfirmationService,
        PointService pointService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.matchConfirmationService = matchConfirmationService;
        this.pointService = pointService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public MatchConfirmationListResponse list(@PathVariable Long groupId, HttpServletRequest request) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = requirePointUser(request);
        return matchConfirmationService.list(groupId, identity.email(), identity.userId());
    }

    @PostMapping("/{matchId}")
    public MatchConfirmationListResponse confirm(
        @PathVariable Long groupId,
        @PathVariable Long matchId,
        HttpServletRequest request
    ) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = requirePointUser(request);
        try {
            return matchConfirmationService.confirm(groupId, matchId, identity.email(), identity.userId());
        } catch (MatchConfirmationForbiddenException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (MatchConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private AuthenticatedRequestResolver.ResolvedRequestIdentity requirePointUser(HttpServletRequest request) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (identity.email().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        if (!pointService.canUsePoints(identity.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Points are not open to this account yet");
        }
        return identity;
    }
}
