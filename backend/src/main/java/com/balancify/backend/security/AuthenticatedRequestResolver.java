package com.balancify.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedRequestResolver {

    public static final String AUTHENTICATED_EMAIL_ATTRIBUTE = "balancify.auth.email";
    public static final String AUTHENTICATED_NICKNAME_ATTRIBUTE = "balancify.auth.nickname";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";
    private static final String USER_NICKNAME_HEADER = "X-USER-NICKNAME";

    private final SupabaseJwtVerifier supabaseJwtVerifier;
    private final SupabaseAuthProperties supabaseAuthProperties;

    public AuthenticatedRequestResolver(
        SupabaseJwtVerifier supabaseJwtVerifier,
        SupabaseAuthProperties supabaseAuthProperties
    ) {
        this.supabaseJwtVerifier = supabaseJwtVerifier;
        this.supabaseAuthProperties = supabaseAuthProperties;
    }

    public ResolvedRequestIdentity resolve(HttpServletRequest request) {
        if (request == null) {
            return ResolvedRequestIdentity.empty();
        }

        String cachedEmail = normalizeEmail(asString(request.getAttribute(AUTHENTICATED_EMAIL_ATTRIBUTE)));
        String cachedNickname = safeTrim(asString(request.getAttribute(AUTHENTICATED_NICKNAME_ATTRIBUTE)));
        if (!cachedEmail.isEmpty()) {
            return new ResolvedRequestIdentity(cachedEmail, cachedNickname, true);
        }

        String bearerToken = extractBearerToken(request.getHeader(AUTHORIZATION_HEADER));
        if (!bearerToken.isEmpty()) {
            Optional<SupabaseJwtVerifier.VerifiedUser> verifiedUser = supabaseJwtVerifier.verify(bearerToken);
            if (verifiedUser.isPresent()) {
                String email = normalizeEmail(verifiedUser.get().email());
                String nickname = safeTrim(verifiedUser.get().nickname());
                if (!email.isEmpty()) {
                    request.setAttribute(AUTHENTICATED_EMAIL_ATTRIBUTE, email);
                    request.setAttribute(AUTHENTICATED_NICKNAME_ATTRIBUTE, nickname);
                    return new ResolvedRequestIdentity(email, nickname, true);
                }
            }

            if (supabaseAuthProperties.isRequireJwt()) {
                return ResolvedRequestIdentity.empty();
            }
        } else if (supabaseAuthProperties.isRequireJwt() && !supabaseAuthProperties.isAllowEmailHeaderFallback()) {
            return ResolvedRequestIdentity.empty();
        }

        if (!supabaseAuthProperties.isAllowEmailHeaderFallback()) {
            return ResolvedRequestIdentity.empty();
        }

        String headerEmail = normalizeEmail(request.getHeader(USER_EMAIL_HEADER));
        if (headerEmail.isEmpty()) {
            return ResolvedRequestIdentity.empty();
        }

        String headerNickname = decodeNickname(request.getHeader(USER_NICKNAME_HEADER));
        request.setAttribute(AUTHENTICATED_EMAIL_ATTRIBUTE, headerEmail);
        request.setAttribute(AUTHENTICATED_NICKNAME_ATTRIBUTE, headerNickname);
        return new ResolvedRequestIdentity(headerEmail, headerNickname, false);
    }

    private String extractBearerToken(String authorizationHeader) {
        String normalized = safeTrim(authorizationHeader);
        if (normalized.isEmpty()) {
            return "";
        }
        if (!normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return safeTrim(normalized.substring(7));
    }

    private String decodeNickname(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.contains("%")) {
            return trimmed;
        }
        try {
            return URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record ResolvedRequestIdentity(
        String email,
        String nickname,
        boolean jwtVerified
    ) {
        public static ResolvedRequestIdentity empty() {
            return new ResolvedRequestIdentity("", "", false);
        }

        public boolean isAuthenticated() {
            return !email.isEmpty();
        }
    }
}

