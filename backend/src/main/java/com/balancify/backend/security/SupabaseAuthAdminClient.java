package com.balancify.backend.security;

import com.balancify.backend.service.exception.AccountDeletionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SupabaseAuthAdminClient {

    private static final String UNAVAILABLE_MESSAGE = "Account deletion is temporarily unavailable";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SupabaseAuthProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public SupabaseAuthAdminClient(SupabaseAuthProperties properties) {
        this(
            properties,
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getVerifyTimeoutMs()))
                .build()
        );
    }

    SupabaseAuthAdminClient(SupabaseAuthProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public void ensureConfigured() {
        if (resolveAuthBaseUrl().isEmpty() || properties.getServiceRoleKey().isEmpty()) {
            throw new AccountDeletionException(AccountDeletionException.Reason.AUTH_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    public void deleteUser(UUID userId) {
        if (userId == null) {
            throw new AccountDeletionException(AccountDeletionException.Reason.AUTH_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        ensureConfigured();

        try {
            String serviceRoleKey = properties.getServiceRoleKey();
            HttpRequest request = HttpRequest
                .newBuilder(URI.create(resolveAuthBaseUrl() + "/admin/users/" + userId))
                .timeout(Duration.ofMillis(properties.getVerifyTimeoutMs()))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .header("Accept", "application/json")
                .DELETE()
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if ((response.statusCode() >= 200 && response.statusCode() < 300) || response.statusCode() == 404) {
                return;
            }
            throw new AccountDeletionException(AccountDeletionException.Reason.AUTH_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AccountDeletionException(AccountDeletionException.Reason.AUTH_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AccountDeletionException(AccountDeletionException.Reason.AUTH_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    /**
     * The login account's email as Supabase Auth holds it, for when the app's public.users copy
     * has none (that copy is only filled by the optional client-side profile sync). Empty when the
     * account no longer exists or has no email.
     */
    public Optional<String> findUserEmail(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        ensureConfigured();

        try {
            String serviceRoleKey = properties.getServiceRoleKey();
            HttpRequest request = HttpRequest
                .newBuilder(URI.create(resolveAuthBaseUrl() + "/admin/users/" + userId))
                .timeout(Duration.ofMillis(properties.getVerifyTimeoutMs()))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
            JsonNode user = OBJECT_MAPPER.readTree(response.body());
            String email = user.path("email").asText("").trim().toLowerCase(Locale.ROOT);
            return email.isEmpty() ? Optional.empty() : Optional.of(email);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private AccountDeletionException unavailable() {
        return new AccountDeletionException(AccountDeletionException.Reason.AUTH_UNAVAILABLE, UNAVAILABLE_MESSAGE);
    }

    private String resolveAuthBaseUrl() {
        String configuredUrl = properties.getSupabaseUrl();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return "";
        }
        String normalizedBase = configuredUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase.endsWith("/auth/v1")
            ? normalizedBase
            : normalizedBase + "/auth/v1";
    }
}
