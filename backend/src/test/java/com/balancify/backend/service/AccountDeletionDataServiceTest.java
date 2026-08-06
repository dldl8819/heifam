package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AccountDeletionDataServiceTest {

    private static final UUID PLACEHOLDER_AUTH_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PLACEHOLDER_EMAIL = "placeholder.user@example.test";
    private static final String ORIGINAL_NICKNAME = "PlaceholderNickname";
    private static final OffsetDateTime TRANSITION_AT =
        OffsetDateTime.parse("2026-07-12T03:00:00Z");
    private static final OffsetDateTime RETAINED_UNTIL =
        OffsetDateTime.parse("2031-07-12T03:00:00Z");

    @Mock
    private AccountPersonalDataRepository accountPersonalDataRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GroupReadCacheService groupReadCacheService;

    @Mock
    private AccessControlService accessControlService;

    private AccountDeletionDataService accountDeletionDataService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-07-12T03:00:00Z"), ZoneOffset.UTC);
        accountDeletionDataService = new AccountDeletionDataService(
            accountPersonalDataRepository,
            playerRepository,
            groupReadCacheService,
            accessControlService,
            fixedClock
        );
    }

    @Test
    void removesAccountIdentifiersWhileRetainingMinimalWithdrawalRecord() {
        Group group = new Group();
        group.setId(42L);

        Player player = new Player();
        player.setId(100L);
        player.setGroup(group);
        player.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setNote("placeholder-personal-note");
        player.setMmr(1450);
        player.setActive(true);
        player.setChatLeftAt(OffsetDateTime.parse("2026-07-01T01:00:00Z"));
        player.setChatLeftReason("placeholder-chat-reason");
        player.setChatRejoinedAt(OffsetDateTime.parse("2026-07-02T01:00:00Z"));
        player.setTierChangeAcknowledgedTier("A");
        player.setTierChangeAcknowledgedAt(OffsetDateTime.parse("2026-07-03T01:00:00Z"));

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(player));

        AccountDeletionDataService.WithdrawalRetentionResult result =
            accountDeletionDataService.retainWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                "  Placeholder.User@Example.Test  "
            );

        assertThat(result.retainedPlayerCount()).isEqualTo(1);
        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(player.getNote()).isNull();
        assertThat(player.isActive()).isFalse();
        assertThat(player.getChatLeftAt()).isEqualTo(TRANSITION_AT);
        assertThat(player.getChatLeftReason())
            .isEqualTo(AccountDeletionDataService.SELF_WITHDRAWAL_REASON);
        assertThat(player.getChatRejoinedAt()).isNull();
        assertThat(player.getTierChangeAcknowledgedTier()).isNull();
        assertThat(player.getTierChangeAcknowledgedAt()).isNull();
        assertThat(player.getAnonymizedAt()).isNull();
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.WITHDRAWN);
        assertThat(player.getIdentityRetainedUntil()).isEqualTo(RETAINED_UNTIL);

        assertThat(player.getId()).isEqualTo(100L);
        assertThat(player.getMmr()).isEqualTo(1450);
        assertThat(player.getGroup()).isSameAs(group);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Player>> savedPlayers = ArgumentCaptor.forClass(Iterable.class);
        verify(playerRepository).saveAllAndFlush(savedPlayers.capture());
        assertThat(savedPlayers.getValue()).containsExactly(player);
        verify(accountPersonalDataRepository).enqueuePendingAuthDeletion(
            PLACEHOLDER_AUTH_USER_ID,
            TRANSITION_AT
        );
        verify(accountPersonalDataRepository).anonymizeHistoricalIdentity(
            PLACEHOLDER_EMAIL,
            List.of(100L),
            "\uD0C8\uD1F4\uD55C \uD68C\uC6D0"
        );
        verify(accountPersonalDataRepository).deleteAccountIdentity(
            PLACEHOLDER_AUTH_USER_ID,
            PLACEHOLDER_EMAIL
        );
        verify(accessControlService).evictAccountCache(PLACEHOLDER_EMAIL);
        verify(groupReadCacheService).evictGroup(42L);
        verify(playerRepository, never())
            .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME);
    }

    @Test
    void linksAllPlayersForAUniquelyOwnedAccessNickname() {
        Player firstCandidate = new Player();
        firstCandidate.setId(101L);
        firstCandidate.setNickname(ORIGINAL_NICKNAME);
        Player secondCandidate = new Player();
        secondCandidate.setId(102L);
        secondCandidate.setNickname(ORIGINAL_NICKNAME);

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(firstCandidate, secondCandidate));

        accountDeletionDataService.linkPlayers(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);

        assertThat(firstCandidate.getAuthUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        assertThat(secondCandidate.getAuthUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        verify(playerRepository).saveAll(List.of(firstCandidate, secondCandidate));
    }

    @Test
    void ignoresInactiveCandidateWhenLinkingAuthenticatedPlayer() {
        Player inactiveCandidate = new Player();
        inactiveCandidate.setId(103L);
        inactiveCandidate.setNickname(ORIGINAL_NICKNAME);
        inactiveCandidate.setActive(false);
        inactiveCandidate.setAuthUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        Player activeCandidate = new Player();
        activeCandidate.setId(104L);
        activeCandidate.setNickname(ORIGINAL_NICKNAME);

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(inactiveCandidate, activeCandidate));

        accountDeletionDataService.linkPlayers(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);

        assertThat(activeCandidate.getAuthUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        assertThat(inactiveCandidate.getAuthUserId()).isNotEqualTo(PLACEHOLDER_AUTH_USER_ID);
        verify(playerRepository).saveAll(List.of(activeCandidate));
    }

    @Test
    void retainsAllUniquelyOwnedPlayersForAccountWithdrawal() {
        Player firstCandidate = new Player();
        firstCandidate.setId(201L);
        firstCandidate.setNickname(ORIGINAL_NICKNAME);
        Player secondCandidate = new Player();
        secondCandidate.setId(202L);
        secondCandidate.setNickname(ORIGINAL_NICKNAME);

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of());
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(firstCandidate, secondCandidate));

        AccountDeletionDataService.WithdrawalRetentionResult result =
            accountDeletionDataService.retainWithdrawnAccount(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);

        assertThat(result.retainedPlayerCount()).isEqualTo(2);
        assertThat(firstCandidate.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(secondCandidate.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(firstCandidate.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.WITHDRAWN);
        assertThat(secondCandidate.getIdentityRetainedUntil()).isEqualTo(RETAINED_UNTIL);
        verify(accountPersonalDataRepository).anonymizeHistoricalIdentity(
            PLACEHOLDER_EMAIL,
            List.of(201L, 202L),
            AccountDeletionDataService.DELETED_MEMBER_LABEL
        );
        verify(accountPersonalDataRepository).deleteAccountIdentity(
            PLACEHOLDER_AUTH_USER_ID,
            PLACEHOLDER_EMAIL
        );
    }

    @Test
    void refusesWithdrawalWhileConfiguredAccessGrantRemains() {
        when(accessControlService.hasConfiguredAccessGrant(PLACEHOLDER_EMAIL)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                PLACEHOLDER_EMAIL
            )
        )
            .isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class)
            .hasMessage("Account withdrawal requires removal from the configured access list");

        verify(playerRepository, never()).saveAllAndFlush(org.mockito.ArgumentMatchers.any());
        verify(accountPersonalDataRepository, never()).enqueuePendingAuthDeletion(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void reEvictsWithdrawalCachesAfterTransactionCommit() {
        Group group = new Group();
        group.setId(42L);
        Player player = new Player();
        player.setId(210L);
        player.setGroup(group);
        player.setNickname(ORIGINAL_NICKNAME);
        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(player));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            accountDeletionDataService.retainWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                PLACEHOLDER_EMAIL
            );
            verify(accessControlService).evictAccountCache(PLACEHOLDER_EMAIL);
            verify(groupReadCacheService).evictGroup(42L);

            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

            verify(accessControlService, times(2)).evictAccountCache(PLACEHOLDER_EMAIL);
            verify(groupReadCacheService, times(2)).evictGroup(42L);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
    @Test
    void retainsInactivePlayerAndRemovesSoleLinkedAccountIdentity() {
        Group group = new Group();
        group.setId(42L);
        Player player = new Player();
        player.setId(301L);
        player.setGroup(group);
        player.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setRace("T");
        player.setNote("placeholder-note");
        player.setActive(true);
        player.setChatLeftAt(TRANSITION_AT.minusDays(1));
        player.setChatLeftReason("운영 비활성");

        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
            PLACEHOLDER_AUTH_USER_ID,
            301L
        )).thenReturn(false);
        when(accountPersonalDataRepository.findAccountEmail(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(Optional.of(PLACEHOLDER_EMAIL));

        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(player);

        assertThat(player.isActive()).isFalse();
        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(player.getRace()).isEqualTo("T");
        assertThat(player.getNote()).isNull();
        assertThat(player.getChatLeftAt()).isEqualTo(TRANSITION_AT.minusDays(1));
        assertThat(player.getChatLeftReason()).isEqualTo("운영 비활성");
        assertThat(player.getAnonymizedAt()).isNull();
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
        assertThat(player.getIdentityRetainedUntil()).isEqualTo(RETAINED_UNTIL.minusDays(1));
        assertThat(outcome.authUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        assertThat(outcome.requiresAuthDeletion()).isTrue();
        verify(accountPersonalDataRepository).enqueuePendingAuthDeletion(
            PLACEHOLDER_AUTH_USER_ID,
            TRANSITION_AT
        );
        verify(playerRepository).saveAndFlush(player);
        verify(accountPersonalDataRepository).anonymizeHistoricalIdentity(
            PLACEHOLDER_EMAIL,
            List.of(301L),
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
        verify(accountPersonalDataRepository).deleteAccountIdentity(
            PLACEHOLDER_AUTH_USER_ID,
            PLACEHOLDER_EMAIL
        );
        verify(accessControlService).evictAccountCache(PLACEHOLDER_EMAIL);
        verify(groupReadCacheService).evictGroup(42L);
    }

    @Test
    void preservesSharedAccountIdentityWhenAnotherActivePlayerUsesTheAuthLink() {
        Group group = new Group();
        group.setId(42L);
        Player player = new Player();
        player.setId(302L);
        player.setGroup(group);
        player.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);

        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
            PLACEHOLDER_AUTH_USER_ID,
            302L
        )).thenReturn(true);
        when(accountPersonalDataRepository.findAccountEmail(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(Optional.of(PLACEHOLDER_EMAIL));

        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(player);

        assertThat(player.isAnonymized()).isFalse();
        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
        assertThat(outcome.requiresAuthDeletion()).isFalse();
        verify(accountPersonalDataRepository).anonymizeHistoricalIdentityInGroup(
            PLACEHOLDER_EMAIL,
            42L,
            List.of(302L),
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
        verify(accountPersonalDataRepository, never()).enqueuePendingAuthDeletion(
            PLACEHOLDER_AUTH_USER_ID,
            TRANSITION_AT
        );
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            PLACEHOLDER_AUTH_USER_ID,
            PLACEHOLDER_EMAIL
        );
        verify(accessControlService, never()).evictAccountCache(PLACEHOLDER_EMAIL);
    }

    @Test
    void refusesSoleLinkedDeactivationWhenStaticAccessGrantRemains() {
        Player player = new Player();
        player.setId(303L);
        player.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);

        when(accountPersonalDataRepository.findAccountEmail(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(Optional.of(PLACEHOLDER_EMAIL));
        when(accessControlService.hasConfiguredAccessGrant(PLACEHOLDER_EMAIL)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> accountDeletionDataService.retainInactivePlayer(player)
        ).isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class);

        assertThat(player.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(player.getAuthUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        verify(playerRepository, never()).saveAndFlush(player);
        verify(accountPersonalDataRepository, never()).enqueuePendingAuthDeletion(
            PLACEHOLDER_AUTH_USER_ID,
            TRANSITION_AT
        );
    }

    @Test
    void refusesInactiveCleanupWhenLinkedAccountEmailIsUnavailable() {
        Player player = new Player();
        player.setId(304L);
        player.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainInactivePlayer(player)
        )
            .isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class)
            .hasMessage("Account deactivation requires a verified account email");

        assertThat(player.isActive()).isTrue();
        assertThat(player.getAuthUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        assertThat(player.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        verify(playerRepository, never()).saveAndFlush(player);
        verify(accountPersonalDataRepository, never()).enqueuePendingAuthDeletion(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void resolvesUniqueNicknameProfileAndRemovesItsUnlinkedAuthIdentity() {
        UUID resolvedAuthUserId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Player player = new Player();
        player.setId(306L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        player.setChatLeftReason("운영 비활성");
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(new AccountPersonalDataRepository.NicknameAccountCandidate(
                PLACEHOLDER_EMAIL,
                resolvedAuthUserId
            )));
        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
            resolvedAuthUserId,
            306L
        )).thenReturn(false);

        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(player);

        assertThat(outcome.authUserId()).isEqualTo(resolvedAuthUserId);
        assertThat(outcome.requiresAuthDeletion()).isTrue();
        verify(accountPersonalDataRepository).enqueuePendingAuthDeletion(
            resolvedAuthUserId,
            TRANSITION_AT
        );
        verify(accountPersonalDataRepository).deleteAccountIdentity(
            resolvedAuthUserId,
            PLACEHOLDER_EMAIL
        );
    }

    @Test
    void removesUniqueNicknameAccessProfileEvenWithoutMirroredAuthAccount() {
        Player player = new Player();
        player.setId(307L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(new AccountPersonalDataRepository.NicknameAccountCandidate(
                PLACEHOLDER_EMAIL,
                null
            )));

        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(player);

        assertThat(outcome.authUserId()).isNull();
        assertThat(outcome.requiresAuthDeletion()).isFalse();
        verify(accountPersonalDataRepository).deleteAccountIdentity(null, PLACEHOLDER_EMAIL);
        verify(accessControlService).evictAccountCache(PLACEHOLDER_EMAIL);
    }

    @Test
    void preservesNicknameResolvedAccountWhenAnotherActivePlayerMayShareIt() {
        UUID resolvedAuthUserId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Player player = new Player();
        player.setId(310L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(new AccountPersonalDataRepository.NicknameAccountCandidate(
                PLACEHOLDER_EMAIL,
                resolvedAuthUserId
            )));
        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
            resolvedAuthUserId,
            310L
        )).thenReturn(false);
        when(playerRepository
            .existsByNicknameIgnoreCaseAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
                ORIGINAL_NICKNAME,
                310L
            )).thenReturn(true);

        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(player);

        assertThat(outcome.authUserId()).isEqualTo(resolvedAuthUserId);
        assertThat(outcome.requiresAuthDeletion()).isFalse();
        verify(accountPersonalDataRepository, never()).enqueuePendingAuthDeletion(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void refusesAmbiguousNicknameOwnershipWithoutChangingPlayer() {
        Player player = new Player();
        player.setId(308L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(
                new AccountPersonalDataRepository.NicknameAccountCandidate(
                    "placeholder.one@example.test",
                    null
                ),
                new AccountPersonalDataRepository.NicknameAccountCandidate(
                    "placeholder.two@example.test",
                    null
                )
            ));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainInactivePlayer(player)
        )
            .isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class)
            .hasMessage("Account deactivation identity ownership could not be resolved");

        assertThat(player.isActive()).isTrue();
        verify(playerRepository, never()).saveAndFlush(player);
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void refusesResolvedNicknameProfileWithConfiguredGrant() {
        Player player = new Player();
        player.setId(309L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(new AccountPersonalDataRepository.NicknameAccountCandidate(
                PLACEHOLDER_EMAIL,
                null
            )));
        when(accessControlService.hasConfiguredAccessGrant(PLACEHOLDER_EMAIL)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainInactivePlayer(player)
        ).isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class);

        assertThat(player.isActive()).isTrue();
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void capsInactiveIdentityRetentionAtFiveYearsFromProcessingTime() {
        Player player = new Player();
        player.setId(305L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        player.setChatLeftAt(TRANSITION_AT.plusYears(1));
        player.setChatLeftReason("운영 비활성");

        accountDeletionDataService.retainInactivePlayer(player);

        assertThat(player.getIdentityRetainedUntil()).isEqualTo(RETAINED_UNTIL);
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
    }
}
