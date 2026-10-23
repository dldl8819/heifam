package com.balancify.backend.config;

import io.sentry.Sentry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Consumer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reports to Sentry an error thrown before a request reaches a controller. Sentry's Spring
 * integration sees only what controllers throw, while the access checks run in filters
 * (AdminKeyFilter, ServiceAccessFilter): with the database down, every request fails there and
 * none would be reported. An error a controller threw comes through here too, and Sentry drops the
 * second report of the same exception. Lost connections (IOException) are not errors of the site.
 * Nothing is sent without SENTRY_DSN; what is sent goes through SentryEventScrubber.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SentryFilterErrorReporter extends OncePerRequestFilter {

    private final Consumer<Throwable> reporter;

    public SentryFilterErrorReporter() {
        this(Sentry::captureException);
    }

    SentryFilterErrorReporter(Consumer<Throwable> reporter) {
        this.reporter = reporter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException failure) {
            reporter.accept(failure);
            throw failure;
        } catch (ServletException failure) {
            reporter.accept(failure.getCause() != null ? failure.getCause() : failure);
            throw failure;
        }
    }
}
