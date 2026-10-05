package com.balancify.backend.service;

import com.balancify.backend.security.AuthenticatedRequestResolver.ResolvedRequestIdentity;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.security.SupabaseJwtVerifier;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountDeletionService.class);

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
        accountDeletionDataService.anonymizeWithdrawnAccount(authUserId, identity.email());
        try {
            supabaseAuthAdminClient.deleteUser(authUserId);
            accountDeletionDataService.completePendingAuthDeletion(authUserId);
        } finally {
            supabaseJwtVerifier.invalidateUser(authUserId.toString());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deactivatePlayer(Long playerId, OffsetDateTime inactiveAt, String inactiveReason) {
        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(playerId, inactiveAt, inactiveReason);
        if (!outcome.requiresAuthDeletion() || outcome.authUserId() == null) {
            return;
        }

        UUID authUserId = outcome.authUserId();
        try {
            supabaseAuthAdminClient.ensureConfigured();
            supabaseAuthAdminClient.deleteUser(authUserId);
            accountDeletionDataService.completePendingAuthDeletion(authUserId);
        } catch (AccountDeletionException exception) {
            if (exception.getReason() != AccountDeletionException.Reason.AUTH_UNAVAILABLE) {
                throw exception;
            }
            // The player is already deactivated and the login account queued for deletion;
            // PendingAuthDeletionWorker tries again, so the admin's request has succeeded.
            LOGGER.warn("Login account deletion deferred to the pending deletion worker");
        } finally {
            supabaseJwtVerifier.invalidateUser(authUserId.toString());
        }
    }

    public UUID resolveRetainedAuthUserId(String expectedRetentionSubjectHash) {
        return accountDeletionDataService.resolveRetainedAuthUserId(expectedRetentionSubjectHash);
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
