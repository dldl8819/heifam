package com.balancify.backend.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EmailHeaderFallbackProfileGuard {

    private final Environment environment;
    private final SupabaseAuthProperties supabaseAuthProperties;

    public EmailHeaderFallbackProfileGuard(
        Environment environment,
        SupabaseAuthProperties supabaseAuthProperties
    ) {
        this.environment = environment;
        this.supabaseAuthProperties = supabaseAuthProperties;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!supabaseAuthProperties.isAllowEmailHeaderFallback()) {
            return;
        }
        if (isLocalOrTestProfile(environment.getActiveProfiles())) {
            return;
        }

        throw new IllegalStateException(
            "AUTH_ALLOW_EMAIL_HEADER_FALLBACK can only be enabled for local or test profiles"
        );
    }

    static boolean isLocalOrTestProfile(String[] activeProfiles) {
        if (activeProfiles == null || activeProfiles.length == 0) {
            return false;
        }

        return Arrays.stream(activeProfiles)
            .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
            .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
    }
}
