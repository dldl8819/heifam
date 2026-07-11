package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
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

@ExtendWith(MockitoExtension.class)
class AccountDeletionDataServiceTest {

    private static final UUID PLACEHOLDER_AUTH_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PLACEHOLDER_EMAIL = "placeholder.user@example.test";
    private static final String ORIGINAL_NICKNAME = "PlaceholderNickname";
    private static final OffsetDateTime ANONYMIZED_AT =
        OffsetDateTime.parse("2026-07-12T03:00:00Z");

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
    void anonymizesDirectIdentifiersWhileRetainingMatchAndMmrAnchors() {
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

        AccountDeletionDataService.AnonymizationResult result =
            accountDeletionDataService.anonymizeAccount(
                PLACEHOLDER_AUTH_USER_ID,
                "  Placeholder.User@Example.Test  "
            );

        assertThat(result.anonymizedPlayerCount()).isEqualTo(1);
        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo("\uD0C8\uD1F4\uD55C \uD68C\uC6D0");
        assertThat(player.getNote()).isNull();
        assertThat(player.isActive()).isFalse();
        assertThat(player.getChatLeftAt()).isNull();
        assertThat(player.getChatLeftReason()).isNull();
        assertThat(player.getChatRejoinedAt()).isNull();
        assertThat(player.getTierChangeAcknowledgedTier()).isNull();
        assertThat(player.getTierChangeAcknowledgedAt()).isNull();
        assertThat(player.getAnonymizedAt()).isEqualTo(ANONYMIZED_AT);

        assertThat(player.getId()).isEqualTo(100L);
        assertThat(player.getMmr()).isEqualTo(1450);
        assertThat(player.getGroup()).isSameAs(group);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Player>> savedPlayers = ArgumentCaptor.forClass(Iterable.class);
        verify(playerRepository).saveAllAndFlush(savedPlayers.capture());
        assertThat(savedPlayers.getValue()).containsExactly(player);
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
    void anonymizesAllLegacyPlayersForAUniquelyOwnedAccessNickname() {
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

        AccountDeletionDataService.AnonymizationResult result =
            accountDeletionDataService.anonymizeAccount(PLACEHOLDER_AUTH_USER_ID, PLACEHOLDER_EMAIL);

        assertThat(result.anonymizedPlayerCount()).isEqualTo(2);
        assertThat(firstCandidate.getNickname()).isEqualTo(AccountDeletionDataService.DELETED_MEMBER_LABEL);
        assertThat(secondCandidate.getNickname()).isEqualTo(AccountDeletionDataService.DELETED_MEMBER_LABEL);
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
}
