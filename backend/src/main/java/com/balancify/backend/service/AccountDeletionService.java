package com.balancify.backend.service;

import com.balancify.backend.security.AuthenticatedRequestResolver.ResolvedRequestIdentity;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.security.SupabaseJwtVerifier;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountDeletionService {

    private final AccountDeletionDataService accountDeletionDataService;
    private final SupabaseAuthAdminClient supabaseAuthAdminClient;
    private final SupabaseJwtVerifier supabaseJwtVerifier;

    public AccountDeletionService(
        AccountDeletionDataService accountDeletionDataService,
        SupabaseAuthAdminClient supabaseAuthAdminClient,
        SupabaseJwtVerifier supabaseJwtVerifier
    ) {
        this.accountDeletionDataService = accountDeletionDataService;
        this.supabaseAuthAdminClient = supabaseAuthAdminClient;
        this.supabaseJwtVerifier = supabaseJwtVerifier;
    }

    public void linkAuthenticatedPlayers(ResolvedRequestIdentity identity) {
        UUID authUserId = resolveVerifiedUserId(identity);
        if (authUserId == null) {
            return;
        }
        accountDeletionDataService.linkPlayers(authUserId, identity.email());
    }

    public void deleteAccount(ResolvedRequestIdentity identity) {
        UUID authUserId = resolveVerifiedUserId(identity);
        if (authUserId == null || identity.email() == null || identity.email().isBlank()) {
            throw new IllegalArgumentException("A verified account is required");
        }

        supabaseAuthAdminClient.ensureConfigured();
        accountDeletionDataService.anonymizeAccount(authUserId, identity.email());
        try {
            supabaseAuthAdminClient.deleteUser(authUserId);
        } finally {
            supabaseJwtVerifier.invalidateUser(authUserId.toString());
        }
    }

    private UUID resolveVerifiedUserId(ResolvedRequestIdentity identity) {
        if (identity == null || !identity.jwtVerified() || identity.userId() == null || identity.userId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(identity.userId().trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
