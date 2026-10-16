package com.balancify.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers on every answer of the backend, refusals and errors included. The site reaches
 * the backend through its own proxy, which sets its own headers and passes none of these on; they
 * cover the backend's address opened directly. An API answers with data, never a page, so nothing
 * of it may be framed, run or loaded as something else, and no address is passed on from it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiSecurityHeadersFilter extends OncePerRequestFilter {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";
    static final String STRICT_TRANSPORT_SECURITY = "max-age=31536000";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        // Only over HTTPS, as a browser heeds it then only. Render ends TLS before the app and says so.
        if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
            response.setHeader("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
        }
        filterChain.doFilter(request, response);
    }
}
