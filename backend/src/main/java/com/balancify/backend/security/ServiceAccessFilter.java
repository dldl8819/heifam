package com.balancify.backend.security;

import com.balancify.backend.service.AccessControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ServiceAccessFilter extends OncePerRequestFilter {

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
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if ("/api/health".equals(path) || "/api/access/me".equals(path)) {
            return true;
        }
        if (isPublicRecentMatchesRequest(method, path)) {
            return true;
        }

        return !(
            path.startsWith("/api/groups/")
                || path.startsWith("/api/matches/")
                || path.startsWith("/api/access/")
        );
    }

    private boolean isPublicRecentMatchesRequest(String method, String path) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        return path != null && path.matches("^/api/groups/[^/]+/matches/recent/?$");
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
