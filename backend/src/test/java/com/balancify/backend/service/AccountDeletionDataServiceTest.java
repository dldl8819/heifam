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
        OffsetDateTime.parse("2027-07-12T03:00:00Z");

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
    void removesAccountAndPlayerIdentifiersImmediatelyOnWithdrawal() {
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

        AccountDeletionDataService.WithdrawalAnonymizationResult result =
            accountDeletionDataService.anonymizeWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                "  Placeholder.User@Example.Test  "
            );

        assertThat(result.anonymizedPlayerCount()).isEqualTo(1);
        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(player.getNote()).isNull();
        assertThat(player.isActive()).isFalse();
        assertThat(player.getChatLeftAt()).isNull();
        assertThat(player.getChatLeftReason()).isNull();
        assertThat(player.getChatRejoinedAt()).isNull();
        assertThat(player.getTierChangeAcknowledgedTier()).isNull();
        assertThat(player.getTierChangeAcknowledgedAt()).isNull();
        assertThat(player.getAnonymizedAt()).isEqualTo(TRANSITION_AT);
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ANONYMIZED);
        assertThat(player.getIdentityRetainedUntil()).isNull();

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
    void refusesToLinkMultiplePlayersFromNicknameAlone() {
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

        assertThat(firstCandidate.getAuthUserId()).isNull();
        assertThat(secondCandidate.getAuthUserId()).isNull();
        verify(playerRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void linksSingleActivePlayerAndClearsStaleRetentionSubject() {
        Player candidate = new Player();
        candidate.setId(105L);
        candidate.setNickname(ORIGINAL_NICKNAME);
        candidate.setRetentionSubjectHash("PLACEHOLDER_RETENTION_HASH");

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(candidate));

        accountDeletionDataService.linkPlayers(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);

        assertThat(candidate.getAuthUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        assertThat(candidate.getRetentionSubjectHash()).isNull();
        verify(playerRepository).saveAll(List.of(candidate));
    }

    @Test
    void refusesToLinkActivePlayerWhenSameNicknameInactiveRecordExists() {
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

        assertThat(activeCandidate.getAuthUserId()).isNull();
        assertThat(inactiveCandidate.getAuthUserId()).isNotEqualTo(PLACEHOLDER_AUTH_USER_ID);
        verify(playerRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesAccountWithdrawalWithoutGuessingBetweenAmbiguousNicknameRecords() {
        Player firstCandidate = new Player();
        firstCandidate.setId(106L);
        firstCandidate.setNickname(ORIGINAL_NICKNAME);
        Player secondCandidate = new Player();
        secondCandidate.setId(107L);
        secondCandidate.setNickname(ORIGINAL_NICKNAME);

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(firstCandidate, secondCandidate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.anonymizeWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                PLACEHOLDER_EMAIL
            )
        )
            .isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class)
            .hasMessage("Account data ownership could not be resolved");

        assertThat(firstCandidate.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(secondCandidate.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        verify(playerRepository, never()).saveAllAndFlush(org.mockito.ArgumentMatchers.any());
        verify(accountPersonalDataRepository, never()).enqueuePendingAuthDeletion(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void refusesAccountWithdrawalWithoutClaimingAPlayerLinkedToAnotherAccount() {
        Player candidate = new Player();
        candidate.setId(108L);
        candidate.setNickname(ORIGINAL_NICKNAME);
        candidate.setAuthUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(candidate));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.anonymizeWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                PLACEHOLDER_EMAIL
            )
        )
            .isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class)
            .hasMessage("Account data ownership could not be resolved");

        assertThat(candidate.getAuthUserId()).isNotEqualTo(PLACEHOLDER_AUTH_USER_ID);
        verify(playerRepository, never()).saveAllAndFlush(org.mockito.ArgumentMatchers.any());
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void anonymizesAllUniquelyOwnedPlayersForAccountWithdrawal() {
        Player firstCandidate = new Player();
        firstCandidate.setId(201L);
        firstCandidate.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        firstCandidate.setNickname(ORIGINAL_NICKNAME);
        Player secondCandidate = new Player();
        secondCandidate.setId(202L);
        secondCandidate.setNickname(ORIGINAL_NICKNAME);
        secondCandidate.setRetentionSubjectHash(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        );

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(firstCandidate));
        when(playerRepository.findByRetentionSubjectHashAndAnonymizedAtIsNull(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        )).thenReturn(List.of(secondCandidate));

        AccountDeletionDataService.WithdrawalAnonymizationResult result =
            accountDeletionDataService.anonymizeWithdrawnAccount(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);

        assertThat(result.anonymizedPlayerCount()).isEqualTo(2);
        assertThat(firstCandidate.getNickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(secondCandidate.getNickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(firstCandidate.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ANONYMIZED);
        assertThat(secondCandidate.getIdentityRetainedUntil()).isNull();
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
    void anonymizesPreviouslyInactivePlayerWhenSharedAccountLaterWithdraws() {
        Player directlyLinked = new Player();
        directlyLinked.setId(203L);
        directlyLinked.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        directlyLinked.setNickname(ORIGINAL_NICKNAME);
        directlyLinked.setActive(true);

        Player previouslyInactive = new Player();
        previouslyInactive.setId(204L);
        previouslyInactive.setNickname(ORIGINAL_NICKNAME);
        previouslyInactive.setActive(false);
        previouslyInactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        previouslyInactive.setChatLeftReason("운영 정책");
        previouslyInactive.setIdentityRetainedUntil(TRANSITION_AT.plusMonths(6));
        previouslyInactive.setRetentionSubjectHash(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        );

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(directlyLinked));
        when(playerRepository.findByRetentionSubjectHashAndAnonymizedAtIsNull(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        )).thenReturn(List.of(previouslyInactive));

        AccountDeletionDataService.WithdrawalAnonymizationResult result =
            accountDeletionDataService.anonymizeWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                PLACEHOLDER_EMAIL
            );

        assertThat(result.anonymizedPlayerCount()).isEqualTo(2);
        assertThat(directlyLinked.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ANONYMIZED);
        assertThat(previouslyInactive.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ANONYMIZED);
        assertThat(previouslyInactive.getNickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(previouslyInactive.getIdentityRetainedUntil()).isNull();
    }

    @Test
    void doesNotAnonymizeSameNicknamePlayerFromAnUnlinkedGroup() {
        Group linkedGroup = new Group();
        linkedGroup.setId(41L);
        Group unrelatedGroup = new Group();
        unrelatedGroup.setId(42L);

        Player directlyLinked = new Player();
        directlyLinked.setId(205L);
        directlyLinked.setGroup(linkedGroup);
        directlyLinked.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        directlyLinked.setNickname(ORIGINAL_NICKNAME);
        directlyLinked.setActive(true);

        Player unrelatedInactive = new Player();
        unrelatedInactive.setId(206L);
        unrelatedInactive.setGroup(unrelatedGroup);
        unrelatedInactive.setNickname(ORIGINAL_NICKNAME);
        unrelatedInactive.setActive(false);
        unrelatedInactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);

        when(accountPersonalDataRepository.findLinkedNickname(PLACEHOLDER_EMAIL))
            .thenReturn(Optional.of(ORIGINAL_NICKNAME));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(directlyLinked));
        AccountDeletionDataService.WithdrawalAnonymizationResult result =
            accountDeletionDataService.anonymizeWithdrawnAccount(
                PLACEHOLDER_AUTH_USER_ID,
                PLACEHOLDER_EMAIL
            );

        assertThat(result.anonymizedPlayerCount()).isEqualTo(1);
        assertThat(unrelatedInactive.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
        assertThat(unrelatedInactive.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
        verify(playerRepository, never())
            .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME);
    }

    @Test
    void refusesWithdrawalWhileConfiguredAccessGrantRemains() {
        when(accessControlService.hasConfiguredAccessGrant(PLACEHOLDER_EMAIL)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.anonymizeWithdrawnAccount(
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
            accountDeletionDataService.anonymizeWithdrawnAccount(
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
        player.setChatLeftReason("운영 정책");

        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatusAndIdNot(
            PLACEHOLDER_AUTH_USER_ID,
            PlayerLifecycleStatus.ACTIVE,
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
        assertThat(player.getChatLeftReason()).isEqualTo("운영 정책");
        assertThat(player.getAnonymizedAt()).isNull();
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
        assertThat(player.getRetentionSubjectHash())
            .isEqualTo(AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID));
        assertThat(player.getIdentityRetainedUntil()).isEqualTo(RETAINED_UNTIL.minusDays(1));
        assertThat(outcome.authUserId()).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
        assertThat(outcome.requiresAuthDeletion()).isTrue();
        verify(accountPersonalDataRepository).enqueuePendingAuthDeletion(
            PLACEHOLDER_AUTH_USER_ID,
            TRANSITION_AT
        );
        verify(playerRepository, never()).clearRetentionSubjectHash(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
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
        player.setChatLeftReason("운영 정책");

        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatusAndIdNot(
            PLACEHOLDER_AUTH_USER_ID,
            PlayerLifecycleStatus.ACTIVE,
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
        assertThat(player.getRetentionSubjectHash())
            .isEqualTo(AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID));
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
        verify(playerRepository, never()).clearRetentionSubjectHash(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        );
        verify(accountPersonalDataRepository, never()).deleteAccountIdentity(
            PLACEHOLDER_AUTH_USER_ID,
            PLACEHOLDER_EMAIL
        );
        verify(accessControlService, never()).evictAccountCache(PLACEHOLDER_EMAIL);
    }

    @Test
    void clearsThePendingAccountLinkOnlyAfterAuthDeletionCompletes() {
        accountDeletionDataService.completePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);

        verify(playerRepository).clearRetentionSubjectHash(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        );
        verify(accountPersonalDataRepository).deletePendingAuthDeletion(PLACEHOLDER_AUTH_USER_ID);
    }

    @Test
    void resolvesARetainedAccountHashOnlyFromAnActiveLinkedAccount() {
        Player activeAccountLink = new Player();
        activeAccountLink.setId(401L);
        activeAccountLink.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        activeAccountLink.setActive(true);
        activeAccountLink.setLifecycleStatus(PlayerLifecycleStatus.ACTIVE);
        when(playerRepository.findDistinctActiveAuthUserIds())
            .thenReturn(List.of(PLACEHOLDER_AUTH_USER_ID));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(activeAccountLink));

        UUID resolved = accountDeletionDataService.resolveRetainedAuthUserId(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        );

        assertThat(resolved).isEqualTo(PLACEHOLDER_AUTH_USER_ID);
    }

    @Test
    void rejectsRetainedAccountLinkWhenItBecomesInactiveBeforeLockValidation() {
        Player inactiveAccountLink = new Player();
        inactiveAccountLink.setId(402L);
        inactiveAccountLink.setAuthUserId(PLACEHOLDER_AUTH_USER_ID);
        inactiveAccountLink.setActive(false);
        inactiveAccountLink.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        when(playerRepository.findDistinctActiveAuthUserIds())
            .thenReturn(List.of(PLACEHOLDER_AUTH_USER_ID));
        when(playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(PLACEHOLDER_AUTH_USER_ID))
            .thenReturn(List.of(inactiveAccountLink));

        assertThat(accountDeletionDataService.resolveRetainedAuthUserId(
            AccountDeletionDataService.retentionSubjectHash(PLACEHOLDER_AUTH_USER_ID)
        )).isNull();
    }

    @Test
    void rejectsAnInvalidRetainedAccountHashWithoutReadingAccountLinks() {
        assertThat(accountDeletionDataService.resolveRetainedAuthUserId("invalid")).isNull();

        verify(playerRepository, never()).findDistinctActiveAuthUserIds();
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
        player.setChatLeftReason("운영 정책");
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(new AccountPersonalDataRepository.NicknameAccountCandidate(
                PLACEHOLDER_EMAIL,
                resolvedAuthUserId
            )));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));
        when(playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatusAndIdNot(
            resolvedAuthUserId,
            PlayerLifecycleStatus.ACTIVE,
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
        player.setChatLeftReason("운영 정책");
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname(ORIGINAL_NICKNAME))
            .thenReturn(List.of(new AccountPersonalDataRepository.NicknameAccountCandidate(
                PLACEHOLDER_EMAIL,
                null
            )));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));

        AccountDeletionDataService.InactivePlayerCleanupOutcome outcome =
            accountDeletionDataService.retainInactivePlayer(player);

        assertThat(outcome.authUserId()).isNull();
        assertThat(outcome.requiresAuthDeletion()).isFalse();
        verify(accountPersonalDataRepository).deleteAccountIdentity(null, PLACEHOLDER_EMAIL);
        verify(accessControlService).evictAccountCache(PLACEHOLDER_EMAIL);
    }

    @Test
    void refusesNicknameOwnershipWhenMultipleVisiblePlayersShareNickname() {
        Player player = new Player();
        player.setId(310L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        player.setChatLeftReason("운영 정책");
        Player sameNickname = new Player();
        sameNickname.setId(311L);
        sameNickname.setNickname(ORIGINAL_NICKNAME);
        sameNickname.setActive(false);
        sameNickname.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        sameNickname.setChatLeftAt(TRANSITION_AT.minusDays(30));
        sameNickname.setChatLeftReason("운영 정책");
        sameNickname.setIdentityRetainedUntil(TRANSITION_AT.plusMonths(6));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player, sameNickname));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainInactivePlayer(player)
        )
            .isInstanceOf(com.balancify.backend.service.exception.AccountDeletionException.class)
            .hasMessage("Account deactivation identity ownership could not be resolved");
    }

    @Test
    void refusesAmbiguousNicknameOwnershipWithoutChangingPlayer() {
        Player player = new Player();
        player.setId(308L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));
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
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));
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
    void rejectsAnInactiveTimeInTheFuture() {
        Player player = new Player();
        player.setId(305L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        player.setChatLeftAt(TRANSITION_AT.plusYears(1));
        player.setChatLeftReason("운영 정책");
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainInactivePlayer(player)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Inactive time cannot be in the future");

        verify(playerRepository, never()).saveAndFlush(player);
    }

    @Test
    void immediatelyAnonymizesInactiveIdentityWhoseRetentionAlreadyExpired() {
        Player player = new Player();
        player.setId(311L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        player.setChatLeftAt(TRANSITION_AT.minusYears(1).minusSeconds(1));
        player.setChatLeftReason("장기 미참여");
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));

        accountDeletionDataService.retainInactivePlayer(player);

        assertThat(player.getNickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(player.getChatLeftReason()).isNull();
        assertThat(player.getAnonymizedAt()).isEqualTo(TRANSITION_AT);
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ANONYMIZED);
        assertThat(player.getIdentityRetainedUntil()).isNull();
        verify(accountPersonalDataRepository).anonymizeHistoricalPlayerIdentity(
            List.of(311L),
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
    }

    @Test
    void locksAndReloadsTheCurrentPlayerBeforeOperationalDeactivation() {
        Player player = new Player();
        player.setId(312L);
        player.setNickname(ORIGINAL_NICKNAME);
        player.setActive(true);
        when(playerRepository.findByIdForIdentityUpdate(312L)).thenReturn(Optional.of(player));
        when(playerRepository.findByNicknameIgnoreCaseAndAnonymizedAtIsNull(ORIGINAL_NICKNAME))
            .thenReturn(List.of(player));

        accountDeletionDataService.retainInactivePlayer(
            312L,
            TRANSITION_AT.minusDays(1),
            "운영 정책"
        );

        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
        assertThat(player.getChatLeftAt()).isEqualTo(TRANSITION_AT.minusDays(1));
        verify(playerRepository).findByIdForIdentityUpdate(312L);
        verify(playerRepository).saveAndFlush(player);
    }

    @Test
    void refusesOperationalDeactivationWhenConcurrentWithdrawalAlreadyHidThePlayer() {
        Player player = new Player();
        player.setId(313L);
        player.setNickname(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.ANONYMIZED);
        player.setAnonymizedAt(TRANSITION_AT);
        when(playerRepository.findByIdForIdentityUpdate(313L)).thenReturn(Optional.of(player));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            accountDeletionDataService.retainInactivePlayer(
                313L,
                TRANSITION_AT.minusDays(1),
                "운영 정책"
            )
        )
            .isInstanceOf(java.util.NoSuchElementException.class)
            .hasMessage("Player not found");

        verify(playerRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
