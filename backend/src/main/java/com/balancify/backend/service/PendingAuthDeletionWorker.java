package com.balancify.backend.service;

import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.security.SupabaseJwtVerifier;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingAuthDeletionWorker {

    private static final int BATCH_SIZE = 25;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final AccountPersonalDataRepository accountPersonalDataRepository;
    private final SupabaseAuthAdminClient supabaseAuthAdminClient;
    private final SupabaseJwtVerifier supabaseJwtVerifier;
    private final Clock clock;

    @Autowired
    public PendingAuthDeletionWorker(
        AccountPersonalDataRepository accountPersonalDataRepository,
        SupabaseAuthAdminClient supabaseAuthAdminClient,
        SupabaseJwtVerifier supabaseJwtVerifier
    ) {
        this(
            accountPersonalDataRepository,
            supabaseAuthAdminClient,
            supabaseJwtVerifier,
            Clock.systemUTC()
        );
    }

    PendingAuthDeletionWorker(
        AccountPersonalDataRepository accountPersonalDataRepository,
        SupabaseAuthAdminClient supabaseAuthAdminClient,
        SupabaseJwtVerifier supabaseJwtVerifier,
        Clock clock
    ) {
        this.accountPersonalDataRepository = accountPersonalDataRepository;
        this.supabaseAuthAdminClient = supabaseAuthAdminClient;
        this.supabaseJwtVerifier = supabaseJwtVerifier;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Scheduled(
        fixedDelayString = "${balancify.privacy.auth-deletion.retry-ms:60000}",
        initialDelayString = "${balancify.privacy.auth-deletion.initial-delay-ms:30000}"
    )
    public void processPendingDeletions() {
        try {
            supabaseAuthAdminClient.ensureConfigured();
        } catch (AccountDeletionException unavailable) {
            return;
        }

        OffsetDateTime claimedAt = OffsetDateTime.now(clock);
        List<UUID> authUserIds = accountPersonalDataRepository.claimPendingAuthDeletions(
            claimedAt,
            claimedAt.minus(RETRY_DELAY),
            BATCH_SIZE
        );
        for (UUID authUserId : authUserIds) {
            boolean deleted = false;
            try {
                supabaseAuthAdminClient.deleteUser(authUserId);
                deleted = true;
            } catch (AccountDeletionException retryLater) {
                // The UUID-only queue retains the item for a later retry.
            } finally {
                supabaseJwtVerifier.invalidateUser(authUserId.toString());
            }
            if (deleted) {
                accountPersonalDataRepository.deletePendingAuthDeletion(authUserId);
            }
        }
    }
}
