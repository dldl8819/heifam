package com.balancify.backend.security;

import com.balancify.backend.service.AccessControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Lets only members with service access reach the API. Every /api path needs it except the few
 * public ones below, so a new endpoint is protected by default.
 *
 * <p>Paths are matched the way Spring MVC matches handlers: percent-decoded, with path parameters
 * ignored. Comparing the raw request URI let /api/%67roups/... skip this check and still reach the
 * /api/groups/... controllers.
 */
@Component
public class ServiceAccessFilter extends OncePerRequestFilter {

    private static final PathPattern API_PATHS = PathPatternParser.defaultInstance.parse("/api/**");
    private static final List<PathPattern> PUBLIC_PATHS = List.of(
        PathPatternParser.defaultInstance.parse("/api/health"),
        // Blocked users still need to learn that they are blocked.
        PathPatternParser.defaultInstance.parse("/api/access/me")
    );
    private static final PathPattern PUBLIC_RECENT_MATCHES =
        PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/matches/recent");

    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public ServiceAccessFilter(
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        PathContainer path;
        try {
            path = PathContainer.parsePath(request.getRequestURI() == null ? "" : request.getRequestURI());
        } catch (IllegalArgumentException malformedPath) {
            // A path that cannot be decoded is checked rather than waved through.
            return false;
        }

        if (!API_PATHS.matches(path)) {
            return true;
        }
        if (PUBLIC_PATHS.stream().anyMatch(publicPath -> publicPath.matches(path))) {
            return true;
        }
        return "GET".equalsIgnoreCase(method) && PUBLIC_RECENT_MATCHES.matches(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (!identity.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!accessControlService.isServiceAccessAllowed(identity.email())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
