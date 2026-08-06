package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class PlayerIdentityRetentionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2031-07-19T03:00:00Z");

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private AccountPersonalDataRepository accountPersonalDataRepository;

    @Mock
    private GroupReadCacheService groupReadCacheService;

    private PlayerIdentityRetentionService retentionService;

    @BeforeEach
    void setUp() {
        retentionService = new PlayerIdentityRetentionService(
            playerRepository,
            accountPersonalDataRepository,
            groupReadCacheService,
            Clock.fixed(Instant.parse("2031-07-19T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void schedulesExpirySweepAfterStartupAndAtARegularInterval() throws NoSuchMethodException {
        Scheduled[] schedules = PlayerIdentityRetentionService.class
            .getDeclaredMethod("anonymizeExpiredIdentities")
            .getAnnotationsByType(Scheduled.class);

        assertThat(schedules).hasSize(2);
        assertThat(schedules).anyMatch(schedule ->
            schedule.cron().contains("identity-retention.sweep-cron")
        );
        assertThat(schedules).anyMatch(schedule ->
            schedule.initialDelayString().contains("identity-retention.initial-delay-ms")
                && schedule.fixedDelayString().contains("identity-retention.fixed-delay-ms")
        );
    }

    @Test
    void conditionallyAnonymizesExpiredIdentityAndMasksHistoricalAudit() {
        Group group = new Group();
        group.setId(42L);
        Player player = new Player();
        player.setId(101L);
        player.setGroup(group);
        player.setNickname("EXPIRED_NICKNAME");
        player.setRace("Z");
        player.setMmr(1450);
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);
        player.setChatLeftAt(NOW.minusYears(5));
        player.setChatLeftReason("회원 본인 요청");
        player.setIdentityRetainedUntil(NOW);

        when(playerRepository
            .findIdentityRetentionExpiryCandidates(NOW, PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL))
            .thenReturn(List.of(player));
        when(playerRepository.anonymizeExpiredIdentity(
            101L,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
            NOW
        )).thenReturn(1);

        int anonymizedCount = retentionService.anonymizeExpiredIdentities();

        assertThat(anonymizedCount).isEqualTo(1);
        verify(playerRepository).anonymizeExpiredIdentity(
            101L,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
            NOW
        );
        verify(accountPersonalDataRepository).anonymizeHistoricalPlayerIdentity(
            List.of(101L),
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
        verify(groupReadCacheService).evictGroup(42L);
    }

    @Test
    void skipsHistoricalMaskingWhenConcurrentReactivationWins() {
        Player player = new Player();
        player.setId(102L);
        player.setNickname("REACTIVATED_NICKNAME");
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        player.setIdentityRetainedUntil(NOW);
        when(playerRepository
            .findIdentityRetentionExpiryCandidates(NOW, PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL))
            .thenReturn(List.of(player));
        when(playerRepository.anonymizeExpiredIdentity(
            102L,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
            NOW
        )).thenReturn(0);

        assertThat(retentionService.anonymizeExpiredIdentities()).isZero();

        verify(accountPersonalDataRepository, never()).anonymizeHistoricalPlayerIdentity(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString()
        );
        verify(groupReadCacheService, never()).evictGroup(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void reportsOnlySuccessfullyAnonymizedRowsFromMixedRaceOutcome() {
        Player expired = new Player();
        expired.setId(103L);
        expired.setActive(false);
        expired.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);
        expired.setIdentityRetainedUntil(NOW);
        Player reactivated = new Player();
        reactivated.setId(104L);
        reactivated.setActive(false);
        reactivated.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        reactivated.setIdentityRetainedUntil(NOW);
        when(playerRepository.findIdentityRetentionExpiryCandidates(
            NOW,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        ))
            .thenReturn(List.of(expired, reactivated));
        when(playerRepository.anonymizeExpiredIdentity(
            103L,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
            NOW
        )).thenReturn(1);
        when(playerRepository.anonymizeExpiredIdentity(
            104L,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
            NOW
        )).thenReturn(0);

        assertThat(retentionService.anonymizeExpiredIdentities()).isEqualTo(1);

        verify(accountPersonalDataRepository).anonymizeHistoricalPlayerIdentity(
            List.of(103L),
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
    }

    @Test
    void anonymizesMissingRetentionDeadlineFailClosed() {
        Player inconsistent = new Player();
        inconsistent.setId(105L);
        inconsistent.setActive(false);
        inconsistent.setLifecycleStatus(PlayerLifecycleStatus.ACTIVE);
        inconsistent.setIdentityRetainedUntil(null);
        when(playerRepository.findIdentityRetentionExpiryCandidates(
            NOW,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        ))
            .thenReturn(List.of(inconsistent));
        when(playerRepository.anonymizeExpiredIdentity(
            105L,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
            NOW
        )).thenReturn(1);

        assertThat(retentionService.anonymizeExpiredIdentities()).isEqualTo(1);

        verify(accountPersonalDataRepository).anonymizeHistoricalPlayerIdentity(
            List.of(105L),
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
    }

    @Test
    void leavesRepositoriesUntouchedWhenNoIdentityHasExpired() {
        when(playerRepository
            .findIdentityRetentionExpiryCandidates(NOW, PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL))
            .thenReturn(List.of());

        assertThat(retentionService.anonymizeExpiredIdentities()).isZero();

        verify(playerRepository, never()).anonymizeExpiredIdentity(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(accountPersonalDataRepository, never()).anonymizeHistoricalPlayerIdentity(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }
}
