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
        // Admins try points out first. Opening them to members takes these two routes as well as
        // balancify.points.members-enabled; the flag alone only lets members earn points.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/points/me"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/points/ranking"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/admin/points/adjustments"),
            AuthType.SUPER_ADMIN_EMAIL
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
        // Predictions are tried out by admins first; PredictionController checks the same.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/predictions"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "PUT",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/predictions/{matchId}"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/predictions/{matchId}/close"),
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
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/income/categories"),
            AuthType.ADMIN_EMAIL
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
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/expense/categories"),
            AuthType.ADMIN_EMAIL
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
            AuthType.ADMIN_EMAIL
        ),
        // Amounts and categories only, no names: this can move to SERVICE_ACCESS once members get the dashboard.
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/dashboard"),
            AuthType.ADMIN_EMAIL
        ),
        new ProtectedRoute(
            "GET",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/ledger/server-costs"),
            AuthType.ADMIN_EMAIL
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
