package com.balancify.backend.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.security.SupabaseJwtVerifier;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingAuthDeletionWorkerTest {

    private static final UUID PLACEHOLDER_AUTH_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime CLAIMED_AT =
        OffsetDateTime.parse("2026-07-19T03:00:00Z");

    @Mock
    private AccountPersonalDataRepository accountPersonalDataRepository;

    @Mock
    private SupabaseAuthAdminClient supabaseAuthAdminClient;

    @Mock
    private SupabaseJwtVerifier supabaseJwtVerifier;

    private PendingAuthDeletionWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PendingAuthDeletionWorker(
            accountPersonalDataRepository,
            supabaseAuthAdminClient,
            supabaseJwtVerifier,
            Clock.fixed(Instant.parse("2026-07-19T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void deletesAuthUserThenRemovesQueueItem() {
        when(accountPersonalDataRepository.claimPendingAuthDeletions(
            CLAIMED_AT,
            CLAIMED_AT.minusMinutes(5),
            25
        )).thenReturn(List.of(PLACEHOLDER_AUTH_USER_ID));

        worker.processPendingDeletions();

        verify(supabaseAuthAdminClient).deleteUser(PLACEHOLDER_AUTH_USER_ID);
        verify(supabaseJwtVerifier).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
        verify(accountPersonalDataRepository).deletePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
    }

    @Test
    void retainsQueueItemWhenAuthDeletionFails() {
        when(accountPersonalDataRepository.claimPendingAuthDeletions(
            CLAIMED_AT,
            CLAIMED_AT.minusMinutes(5),
            25
        )).thenReturn(List.of(PLACEHOLDER_AUTH_USER_ID));
        doThrow(new AccountDeletionException("placeholder failure"))
            .when(supabaseAuthAdminClient)
            .deleteUser(PLACEHOLDER_AUTH_USER_ID);

        worker.processPendingDeletions();

        verify(supabaseJwtVerifier).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
        verify(accountPersonalDataRepository, never())
            .deletePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
    }

    @Test
    void leavesQueueUntouchedWhenAuthAdminIsUnavailable() {
        doThrow(new AccountDeletionException("placeholder unavailable"))
            .when(supabaseAuthAdminClient)
            .ensureConfigured();

        worker.processPendingDeletions();

        verify(accountPersonalDataRepository, never()).claimPendingAuthDeletions(
            CLAIMED_AT,
            CLAIMED_AT.minusMinutes(5),
            25
        );
    }
}
