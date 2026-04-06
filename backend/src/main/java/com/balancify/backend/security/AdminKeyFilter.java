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
            "POST",
            PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/matches"),
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
            AuthType.ADMIN_EMAIL
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
            AuthType.ADMIN_EMAIL
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
            "POST",
            PathPatternParser.defaultInstance.parse("/api/admin/rating/recalculate"),
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
            return method.equalsIgnoreCase(requestMethod) && pattern.matches(requestPath);
        }
    }

    private enum AuthType {
        ADMIN_EMAIL,
        SUPER_ADMIN_EMAIL,
        SERVICE_ACCESS
    }
}
