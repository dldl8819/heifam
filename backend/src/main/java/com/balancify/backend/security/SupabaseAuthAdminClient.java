package com.balancify.backend.security;

import com.balancify.backend.service.exception.AccountDeletionException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SupabaseAuthAdminClient {

    private static final String UNAVAILABLE_MESSAGE = "Account deletion is temporarily unavailable";

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
            throw new AccountDeletionException(UNAVAILABLE_MESSAGE);
        }
    }

    public void deleteUser(UUID userId) {
        if (userId == null) {
            throw new AccountDeletionException(UNAVAILABLE_MESSAGE);
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
            throw new AccountDeletionException(UNAVAILABLE_MESSAGE);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AccountDeletionException(UNAVAILABLE_MESSAGE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AccountDeletionException(UNAVAILABLE_MESSAGE);
        }
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
