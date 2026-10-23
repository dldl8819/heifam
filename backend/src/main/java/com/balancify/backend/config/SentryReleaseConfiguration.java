package com.balancify.backend.config;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Names the release a Sentry event comes from: the commit Render deployed (RENDER_GIT_COMMIT), so
 * an error points at the deploy that brought it. A release set on purpose (SENTRY_RELEASE) wins;
 * without either, events carry none. Sentry's Spring Boot integration applies this option bean.
 */
@Configuration
public class SentryReleaseConfiguration {

    @Bean
    Sentry.OptionsConfiguration<SentryOptions> sentryReleaseFromDeploy(Environment environment) {
        return options -> applyRelease(options, environment.getProperty("RENDER_GIT_COMMIT"));
    }

    static void applyRelease(SentryOptions options, String commit) {
        if (options.getRelease() == null && commit != null && !commit.isBlank()) {
            options.setRelease(commit.trim());
        }
    }
}
