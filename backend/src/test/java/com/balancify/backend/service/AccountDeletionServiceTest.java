package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.balancify.backend.security.AuthenticatedRequestResolver.ResolvedRequestIdentity;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.security.SupabaseJwtVerifier;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final UUID PLACEHOLDER_AUTH_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PLACEHOLDER_EMAIL = "placeholder.user@example.test";
    private static final OffsetDateTime INACTIVE_AT =
        OffsetDateTime.parse("2026-07-12T03:00:00Z");
    private static final String INACTIVE_REASON = "운영 정책";

    @Mock
    private AccountDeletionDataService accountDeletionDataService;

    @Mock
    private SupabaseAuthAdminClient supabaseAuthAdminClient;

    @Mock
    private SupabaseJwtVerifier supabaseJwtVerifier;

    private AccountDeletionService accountDeletionService;

    @BeforeEach
    void setUp() {
        accountDeletionService = new AccountDeletionService(
            accountDeletionDataService,
            supabaseAuthAdminClient,
            supabaseJwtVerifier
        );
    }

    @Test
    void deletesVerifiedJwtAccountAndInvalidatesCachedIdentity() {
        ResolvedRequestIdentity identity = verifiedIdentity();

        accountDeletionService.deleteAccount(identity);

        InOrder deletionOrder = inOrder(
            supabaseAuthAdminClient,
            accountDeletionDataService,
            supabaseJwtVerifier
        );
        deletionOrder.verify(supabaseAuthAdminClient).ensureConfigured();
        deletionOrder.verify(accountDeletionDataService)
            .anonymizeWithdrawnAccount(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);
        deletionOrder.verify(supabaseAuthAdminClient).deleteUser(PLACEHOLDER_AUTH_USER_ID);
        deletionOrder.verify(accountDeletionDataService)
            .completePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
        deletionOrder.verify(supabaseJwtVerifier).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
    }

    @Test
    void rejectsUnverifiedHeaderIdentityBeforeChangingAccountData() {
        ResolvedRequestIdentity unverifiedIdentity = new ResolvedRequestIdentity(
            PLACEHOLDER_EMAIL,
            "PlaceholderNickname",
            false,
            PLACEHOLDER_AUTH_USER_ID.toString()
        );

        assertThatThrownBy(() -> accountDeletionService.deleteAccount(unverifiedIdentity))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountDeletionDataService, supabaseAuthAdminClient, supabaseJwtVerifier);
    }

    @Test
    void rejectsVerifiedIdentityWithoutProviderUserId() {
        ResolvedRequestIdentity identityWithoutProviderId = new ResolvedRequestIdentity(
            PLACEHOLDER_EMAIL,
            "PlaceholderNickname",
            true,
            "placeholder-invalid-user-id"
        );

        assertThatThrownBy(() -> accountDeletionService.deleteAccount(identityWithoutProviderId))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountDeletionDataService, supabaseAuthAdminClient, supabaseJwtVerifier);
    }

    @Test
    void invalidatesCachedIdentityWhenProviderDeletionFails() {
        ResolvedRequestIdentity identity = verifiedIdentity();
        org.mockito.Mockito.doThrow(new AccountDeletionException("placeholder deletion failure"))
            .when(supabaseAuthAdminClient)
            .deleteUser(PLACEHOLDER_AUTH_USER_ID);

        assertThatThrownBy(() -> accountDeletionService.deleteAccount(identity))
            .isInstanceOf(AccountDeletionException.class);

        verify(accountDeletionDataService)
            .anonymizeWithdrawnAccount(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);
        verify(accountDeletionDataService, never())
            .completePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
        verify(supabaseJwtVerifier).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
    }

    @Test
    void deletesSoleLinkedAuthUserWhenPlayerIsDeactivated() {
        when(accountDeletionDataService.retainInactivePlayer(301L, INACTIVE_AT, INACTIVE_REASON))
            .thenReturn(new AccountDeletionDataService.InactivePlayerCleanupOutcome(
                PLACEHOLDER_AUTH_USER_ID,
                true
            ));

        accountDeletionService.deactivatePlayer(301L, INACTIVE_AT, INACTIVE_REASON);

        verify(supabaseAuthAdminClient).ensureConfigured();
        verify(supabaseAuthAdminClient).deleteUser(PLACEHOLDER_AUTH_USER_ID);
        verify(accountDeletionDataService).completePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
        verify(supabaseJwtVerifier).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
    }

    @Test
    void preservesSharedAuthUserWhenPlayerIsDeactivated() {
        when(accountDeletionDataService.retainInactivePlayer(302L, INACTIVE_AT, INACTIVE_REASON))
            .thenReturn(new AccountDeletionDataService.InactivePlayerCleanupOutcome(
                PLACEHOLDER_AUTH_USER_ID,
                false
            ));

        accountDeletionService.deactivatePlayer(302L, INACTIVE_AT, INACTIVE_REASON);

        verify(supabaseAuthAdminClient, never()).deleteUser(PLACEHOLDER_AUTH_USER_ID);
        verify(supabaseJwtVerifier, never()).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
    }

    @Test
    void retainsPendingDeletionAndInvalidatesCacheWhenAuthDeletionFails() {
        when(accountDeletionDataService.retainInactivePlayer(303L, INACTIVE_AT, INACTIVE_REASON))
            .thenReturn(new AccountDeletionDataService.InactivePlayerCleanupOutcome(
                PLACEHOLDER_AUTH_USER_ID,
                true
            ));
        org.mockito.Mockito.doThrow(new AccountDeletionException("placeholder failure"))
            .when(supabaseAuthAdminClient)
            .deleteUser(PLACEHOLDER_AUTH_USER_ID);

        assertThatThrownBy(() ->
            accountDeletionService.deactivatePlayer(303L, INACTIVE_AT, INACTIVE_REASON)
        )
            .isInstanceOf(AccountDeletionException.class);

        verify(accountDeletionDataService, never())
            .completePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
        verify(supabaseJwtVerifier).invalidateUser(PLACEHOLDER_AUTH_USER_ID.toString());
    }

    @Test
    void delegatesRetainedAccountLinkResolutionToTheDataService() {
        String retainedHash = AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID);
        when(accountDeletionDataService.resolveRetainedAuthUserId(retainedHash))
            .thenReturn(PLACEHOLDER_AUTH_USER_ID);

        org.assertj.core.api.Assertions.assertThat(
            accountDeletionService.resolveRetainedAuthUserId(retainedHash)
        ).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
    }

    private ResolvedRequestIdentity verifiedIdentity() {
        return new ResolvedRequestIdentity(
            PLACEHOLDER_EMAIL,
            "PlaceholderNickname",
            true,
            PLACEHOLDER_AUTH_USER_ID.toString()
        );
    }
}
