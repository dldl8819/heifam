package com.balancify.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SupabaseJwtVerifier {

    private final RestClient restClient;
    private final SupabaseAuthProperties supabaseAuthProperties;
    private final ConcurrentMap<String, CachedVerification> verificationCache = new ConcurrentHashMap<>();

    public SupabaseJwtVerifier(
        SupabaseAuthProperties supabaseAuthProperties
    ) {
        this.supabaseAuthProperties = supabaseAuthProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(this.supabaseAuthProperties.getVerifyTimeoutMs());
        requestFactory.setReadTimeout(this.supabaseAuthProperties.getVerifyTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public Optional<VerifiedUser> verify(String bearerToken) {
        String normalizedToken = safeTrim(bearerToken);
        if (normalizedToken.isEmpty()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        CachedVerification cachedVerification = verificationCache.get(normalizedToken);
        if (cachedVerification != null && cachedVerification.expiresAtEpochMs() > now) {
            return Optional.of(cachedVerification.user());
        }

        String userEndpoint = resolveUserEndpoint();
        if (userEndpoint.isEmpty()) {
            return Optional.empty();
        }

        try {
            JsonNode responseBody = restClient
                .get()
                .uri(URI.create(userEndpoint))
                .headers(headers -> {
                    headers.setBearerAuth(normalizedToken);
                    String apiKey = safeTrim(supabaseAuthProperties.getSupabaseApiKey());
                    if (!apiKey.isEmpty()) {
                        headers.set("apikey", apiKey);
                    }
                    headers.set(HttpHeaders.ACCEPT, "application/json");
                })
                .retrieve()
                .body(JsonNode.class);

            if (responseBody == null) {
                return Optional.empty();
            }

            String email = normalizeEmail(responseBody.path("email").asText(""));
            if (email.isEmpty()) {
                return Optional.empty();
            }

            String nickname = resolveNickname(responseBody.path("user_metadata"));
            VerifiedUser verifiedUser = new VerifiedUser(email, nickname);
            long ttlMillis = (long) supabaseAuthProperties.getVerificationCacheTtlSeconds() * 1000L;
            if (ttlMillis > 0) {
                verificationCache.put(normalizedToken, new CachedVerification(verifiedUser, now + ttlMillis));
            }

            return Optional.of(verifiedUser);
        } catch (RestClientException restClientException) {
            verificationCache.remove(normalizedToken);
            return Optional.empty();
        }
    }

    private String resolveUserEndpoint() {
        String configuredUrl = safeTrim(supabaseAuthProperties.getSupabaseUrl());
        if (configuredUrl.isEmpty()) {
            return "";
        }

        String normalizedBase = configuredUrl.endsWith("/")
            ? configuredUrl.substring(0, configuredUrl.length() - 1)
            : configuredUrl;

        if (normalizedBase.endsWith("/auth/v1")) {
            return normalizedBase + "/user";
        }
        return normalizedBase + "/auth/v1/user";
    }

    private String resolveNickname(JsonNode userMetadataNode) {
        if (userMetadataNode == null || userMetadataNode.isNull() || !userMetadataNode.isObject()) {
            return "";
        }

        String[] candidates = new String[] {
            userMetadataNode.path("nickname").asText(""),
            userMetadataNode.path("full_name").asText(""),
            userMetadataNode.path("name").asText(""),
            userMetadataNode.path("preferred_username").asText("")
        };

        for (String candidate : candidates) {
            String normalized = safeTrim(candidate);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        return "";
    }

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record VerifiedUser(
        String email,
        String nickname
    ) {
    }

    private record CachedVerification(
        VerifiedUser user,
        long expiresAtEpochMs
    ) {
    }
}
