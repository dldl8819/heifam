package com.balancify.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.balancify.backend.service.AccessControlService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
public class AdminKeyFilter extends OncePerRequestFilter {

    private static final List<ProtectedRoute> PROTECTED_ROUTES = List.of(
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/import"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/dormant"),
            AuthType.ADMIN_EMAIL
        ),
        // 휴면: an admin sets a player aside (PUT) or wakes them (DELETE).
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/{playerId}/dormant"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/{playerId}/dormant"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse(
                "/api/groups/{groupId}/players/{playerId}/last-participation"
            ),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/matches"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/matches/history"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        // A member may read their own row here; the controller is what checks that it is theirs.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/{playerId}/teammate-stats"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "PATCH",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/{playerId}/mmr"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PATCH",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/{playerId}"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/players/{playerId}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/matches/import"),
            AuthType.ADMIN_EMAIL
        ),
        // Points are open to members (balancify.points.members-enabled); PointController checks the same.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/points/me"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/points/ranking"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/points/ranking/{accountId}"),
            AuthType.SERVICE_ACCESS
        ),
        // Players confirm the results of their matches for a point; MatchConfirmationController checks the points flag.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/match-confirmations"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/match-confirmations/{matchId}"),
            AuthType.SERVICE_ACCESS
        ),
        // Captain drafts are run from an admin-only screen, and these calls change draft data.
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/captain-drafts"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/captain-drafts/latest"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/captain-drafts/{draftId}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/captain-drafts/{draftId}/entries"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/captain-drafts/{draftId}/pick"),
            AuthType.ADMIN_EMAIL
        ),
        // Prize events: admins see them while points are tried out, super admins run them.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/prize-events"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/prize-events"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/prize-events/{eventId}/confirm"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/prize-events/{eventId}/cancel"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        // Members predict (balancify.predictions.members-enabled); closing a match early stays with admins.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/predictions"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/predictions/{matchId}"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/predictions/{matchId}/close"),
            AuthType.ADMIN_EMAIL
        ),
        // Notifications reach every member (balancify.notifications.members-enabled); NotificationController checks the same.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notifications"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notifications/read"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/notifications/push-config"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/notifications/push-subscriptions"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/notifications/push-subscriptions/remove"),
            AuthType.SERVICE_ACCESS
        ),
        // Members run multi-balance series; cancelling one stays with admins, as BalanceSeriesController checks.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/balance-series"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/balance-series"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/balance-series/{seriesId}/cancel"),
            AuthType.ADMIN_EMAIL
        ),
        // Team tournaments are tried out by admins first; TeamTournamentController checks the same.
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/tournaments"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/tournaments/latest"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/tournaments/{tournamentId}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/tournaments/{tournamentId}/cancel"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/team-scores"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/matches/manual"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/matches/{id}/result"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "PATCH",
            PathPatternParser.defaultInstance.parse("/api/matches/{id}/result"),
            AuthType.SERVICE_ACCESS
        ),
        // A member calls off a match they set up before it is played (MatchResultService checks
        // whose it is); deleting stays with admins.
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/matches/{id}/cancel"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/matches/{id}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/access/admins"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/access/admins"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/access/admins/{email}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/access/admins/{email}/mmr-access"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/admin/rating/recalculate"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        // Admins see only what match result editors did; the controller narrows the logs to that.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/admin/audit-logs"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/access/result-editors"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/access/result-editors"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/access/result-editors/{email}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/access/allowed-users"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/access/allowed-users"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/access/allowed-users/{email}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/access/allowed-users/{email}/nickname"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notices"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notices/{noticeId}"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notices"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notices/{noticeId}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notices/{noticeId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        // Voting and adding an option are for whoever may open the notice (NoticeService decides);
        // taking an option away, with the votes on it, is for admins.
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse(
                "/api/groups/{groupId}/notices/{noticeId}/vote/options/{optionId}"
            ),
            AuthType.ADMIN_EMAIL
        ),
        // Admins upload the images of a notice; a member reads those of a notice they may open,
        // which the service decides per image.
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notice-images"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notice-images/{imageId}"),
            AuthType.SERVICE_ACCESS
        ),
        // Members file and call off their own nickname requests; deciding one is for admins.
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse(
                "/api/groups/{groupId}/nickname-requests/{requestId}/decision"
            ),
            AuthType.ADMIN_EMAIL
        ),
        // Members read the records of prize draws; an admin saves one, a super admin removes one.
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/prize-draws"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/prize-draws/{drawId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        // Members read the donation ledger on the notices page; only super admins change it.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income/categories"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income/{entryId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income/{entryId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income/import"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense/categories"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense/{entryId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense/{entryId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense/import"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/summary"),
            AuthType.SERVICE_ACCESS
        ),
        // Amounts and categories only, no names: this can move to SERVICE_ACCESS once members get the dashboard.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/dashboard"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/server-costs"),
            AuthType.SERVICE_ACCESS
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/server-costs"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/server-costs/{costId}"),
            AuthType.SUPER_ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "DELETE",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/server-costs/{costId}"),
            AuthType.SUPER_ADMIN_EMAIL
        )
    );

    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public AdminKeyFilter(
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        PathContainer pathContainer = PathContainer.parsePath(path);

        return PROTECTED_ROUTES
            .stream()
            .noneMatch(route -> route.matches(method, pathContainer));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        PathContainer requestPath = PathContainer.parsePath(request.getRequestURI());
        String requestMethod = request.getMethod();

        ProtectedRoute matchedRoute = PROTECTED_ROUTES
            .stream()
            .filter(route -> route.matches(requestMethod, requestPath))
            .findFirst()
            .orElse(null);

        if (matchedRoute == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isAuthorized(request, matchedRoute.authType())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(HttpServletRequest request, AuthType authType) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (!identity.isAuthenticated()) {
            return false;
        }

        String requestEmail = identity.email();
        if (authType == AuthType.ADMIN_EMAIL) {
            return accessControlService.isAdminEmail(requestEmail);
        }

        if (authType == AuthType.SUPER_ADMIN_EMAIL) {
            return accessControlService.isSuperAdminEmail(requestEmail);
        }

        if (authType == AuthType.SERVICE_ACCESS) {
            return accessControlService.isServiceAccessAllowed(requestEmail);
        }

        return false;
    }

    private record ProtectedRoute(
        String method,
        PathPattern pattern,
        AuthType authType
    ) {
        private boolean matches(String requestMethod, PathContainer requestPath) {
            // Spring MVC answers HEAD with the GET handler, so HEAD must meet the GET rule.
            String effectiveMethod = "HEAD".equalsIgnoreCase(requestMethod) ? "GET" : requestMethod;
            return method.equalsIgnoreCase(effectiveMethod) && pattern.matches(requestPath);
        }
    }

    private enum AuthType {
        ADMIN_EMAIL,
        SUPER_ADMIN_EMAIL,
        SERVICE_ACCESS
    }
}
