package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EmailHeaderFallbackProfileGuardTest {

    @Test
    void allowsFallbackForLocalProfile() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setAllowEmailHeaderFallback(true);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        EmailHeaderFallbackProfileGuard guard = new EmailHeaderFallbackProfileGuard(environment, properties);

        assertThatCode(guard::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void allowsFallbackForTestProfile() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setAllowEmailHeaderFallback(true);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        EmailHeaderFallbackProfileGuard guard = new EmailHeaderFallbackProfileGuard(environment, properties);

        assertThatCode(guard::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void rejectsFallbackForProdLikeProfile() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setAllowEmailHeaderFallback(true);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        EmailHeaderFallbackProfileGuard guard = new EmailHeaderFallbackProfileGuard(environment, properties);

        assertThatThrownBy(guard::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AUTH_ALLOW_EMAIL_HEADER_FALLBACK can only be enabled for local or test profiles");
    }

    @Test
    void rejectsFallbackWhenNoActiveProfileIsSet() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setAllowEmailHeaderFallback(true);

        EmailHeaderFallbackProfileGuard guard = new EmailHeaderFallbackProfileGuard(
            new MockEnvironment(),
            properties
        );

        assertThatThrownBy(guard::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AUTH_ALLOW_EMAIL_HEADER_FALLBACK can only be enabled for local or test profiles");
    }

    @Test
    void allowsDisabledFallbackForAnyProfile() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setAllowEmailHeaderFallback(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        EmailHeaderFallbackProfileGuard guard = new EmailHeaderFallbackProfileGuard(environment, properties);

        assertThatCode(guard::validateConfiguration).doesNotThrowAnyException();
    }
}
