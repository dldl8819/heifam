package com.balancify.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiNoStoreFilter extends OncePerRequestFilter {

    private static final PathPattern API_PATHS = PathPatternParser.defaultInstance.parse("/api/**");

    // Decoded like Spring MVC does, so an encoded path such as /%61pi/... still gets no-store.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        try {
            return !API_PATHS.matches(PathContainer.parsePath(path));
        } catch (IllegalArgumentException malformedPath) {
            return false;
        }
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        filterChain.doFilter(request, response);
    }
}
