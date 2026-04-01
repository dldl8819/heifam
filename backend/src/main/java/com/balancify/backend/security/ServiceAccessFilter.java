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

    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";

    private final AccessControlService accessControlService;

    public ServiceAccessFilter(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
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
        String requestEmail = safeTrim(request.getHeader(USER_EMAIL_HEADER));
        if (requestEmail.isEmpty() || !accessControlService.isServiceAccessAllowed(requestEmail)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
