package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchParticipantRepository.PlayerLastPlayedAtProjection;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.GroupRepository;
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

@ExtendWith(MockitoExtension.class)
class DormancyMmrDecayServiceTest {

    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-04-02T00:00:00Z");
    private static final int MMR_DROP_PER_PERIOD = 30;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private GroupRepository groupRepository;

    private DormancyMmrDecayService service;

    @BeforeEach
    void setUp() {
        service = new DormancyMmrDecayService(
            playerRepository,
            matchParticipantRepository,
            groupRepository,
            true,
            15,
            MMR_DROP_PER_PERIOD,
            5,
            2.0,
            new GroupReadCacheService(0),
            Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC)
        );
    }

    @Test
    void scheduledSweepAppliesDecayForAllGroups() {
        Group first = group(1L);
        Group second = group(2L);
        Player firstPlayer = player(9L, first, 930, "2026-03-03T00:00:00Z");
        Player secondPlayer = player(10L, second, 880, "2026-03-03T00:00:00Z");

        when(groupRepository.findAll()).thenReturn(List.of(first, second));
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(firstPlayer));
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(2L)).thenReturn(List.of(secondPlayer));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());
        when(matchParticipantRepository.findLastPlayedAtByGroupId(2L)).thenReturn(List.of());

        service.applyAllGroupsDormancyDecay();

        assertThat(firstPlayer.getMmr()).isEqualTo(870);
        assertThat(secondPlayer.getMmr()).isEqualTo(820);
    }

    @Test
    void capsLongDormancyDecayAtTwoTierFloor() {
        Group group = group(1L);
        Player player = player(9L, group, 930, "2025-07-06T00:00:00Z");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(400);
        assertThat(player.getTier()).isEqualTo("B-");
        assertThat(player.getLastDormancyMmrDecayAt())
            .isEqualTo(OffsetDateTime.parse("2026-04-02T00:00:00Z"));
        assertThat(player.getDormantSince()).isEqualTo(OffsetDateTime.parse("2025-07-21T00:00:00Z"));
        assertThat(player.getReturnedAt()).isNull();
        assertThat(player.getReturnBoostGamesRemaining()).isZero();
        assertThat(player.getReturnBoostMultiplier()).isEqualTo(2.0);
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void dropsActualMmrForOneDormancyPeriodWhenWithinTwoTierCap() {
        Group group = group(1L);
        Player player = player(9L, group, 930, "2026-03-18T00:00:00Z");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(900);
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void capsLongDormancyForATierPlayerAtBPlusFloor() {
        Group group = group(1L);
        Player player = player(9L, group, 1680, "2025-07-06T00:00:00Z");
        player.setTier("A");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1200);
        assertThat(player.getTier()).isEqualTo("A");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void capsLongDormancyForBPlusTierPlayerAtBMinusFloor() {
        Group group = group(1L);
        Player player = player(9L, group, 1320, "2025-07-06T00:00:00Z");
        player.setTier("B+");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(800);
        assertThat(player.getTier()).isEqualTo("B+");
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("B-");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void capsDormancyAtTwoStepsBelowLifetimeHighestTier() {
        Group group = group(1L);
        Player player = player(9L, group, 1320, "2025-07-06T00:00:00Z");
        player.setHighestAchievedTier("A-");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1000);
        assertThat(player.getTier()).isEqualTo("B+");
        assertThat(player.getHighestAchievedTier()).isEqualTo("A-");
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("B");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void raisesAnExistingDormancyEpisodeFloorToTheLifetimeHighestTierPolicy() {
        Group group = group(1L);
        Player player = player(9L, group, 1050, "2026-01-02T00:00:00Z");
        player.setTier("B+");
        player.setHighestAchievedTier("A-");
        player.setDormantSince(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        player.setDormancyEpisodeFloorTier("B-");
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-03-03T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1000);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("B");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void keepsInitialAPlusFloorAfterMonthlyTierRefreshDuringSameDormancyEpisode() {
        Group group = group(1L);
        Player player = player(9L, group, 1930, "2025-07-06T00:00:00Z");
        player.setTier("A+");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of(player));

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1400);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("A-");

        MonthlyTierRefreshService monthlyTierRefreshService = new MonthlyTierRefreshService(
            playerRepository,
            true,
            "Asia/Seoul",
            new GroupReadCacheService(0),
            Clock.fixed(
                OffsetDateTime.parse("2026-04-30T14:59:59Z").toInstant(),
                ZoneOffset.UTC
            )
        );
        monthlyTierRefreshService.applyMonthlyTierRefreshIfDue();

        assertThat(player.getTier()).isEqualTo("A-");

        DormancyMmrDecayService laterService = new DormancyMmrDecayService(
            playerRepository,
            matchParticipantRepository,
            groupRepository,
            true,
            15,
            MMR_DROP_PER_PERIOD,
            5,
            2.0,
            new GroupReadCacheService(0),
            Clock.fixed(
                OffsetDateTime.parse("2026-06-02T00:00:00Z").toInstant(),
                ZoneOffset.UTC
            )
        );
        laterService.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1400);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("A-");
    }

    @Test
    void usesStoredTierInsteadOfLiveMmrTierForDormancyCap() {
        Group group = group(1L);
        Player player = player(9L, group, 1320, "2025-07-06T00:00:00Z");
        player.setMmr(1680);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1140);
        assertThat(player.getTier()).isEqualTo("B+");
        assertThat(player.getHighestAchievedTier()).isEqualTo("B+");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void appliesConfiguredDormancyFloorTierWhenItIsHigherThanTwoStepCap() {
        Group group = group(1L);
        Player player = player(9L, group, 1930, "2025-07-06T00:00:00Z");
        player.setTier("A+");
        player.setDormancyMmrFloorTier("A");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1600);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("A-");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void doesNotIncreaseMmrWhenConfiguredDormancyFloorIsAboveCurrentMmr() {
        Group group = group(1L);
        Player player = player(9L, group, 1500, "2026-01-02T00:00:00Z");
        player.setTier("A");
        player.setDormancyMmrFloorTier("A");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1500);
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void doesNotApplyTheSameDormancyPeriodTwice() {
        Group group = group(1L);
        Player player = player(9L, group, 330, "2026-01-02T00:00:00Z");
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-04-02T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(330);
        verify(playerRepository, never()).saveAll(anyList());
    }

    @Test
    void resetsDormancyAnchorAfterRecentMatch() {
        Group group = group(1L);
        Player player = player(9L, group, 930, "2026-01-02T00:00:00Z");
        player.setDormantSince(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        player.setDormancyEpisodeFloorTier("B-");
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L))
            .thenReturn(List.of(lastPlayedAt(9L, "2026-03-20T00:00:00Z")));

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(930);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("B-");
        verify(playerRepository, never()).saveAll(anyList());
    }

    @Test
    void keepsCurrentEpisodeFloorWhenLatestRecordedMatchPredatesDormancy() {
        Group group = group(1L);
        Player player = player(9L, group, 1420, "2026-01-02T00:00:00Z");
        player.setTier("B+");
        player.setDormantSince(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        player.setDormancyEpisodeFloorTier("A-");
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-03-18T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L))
            .thenReturn(List.of(lastPlayedAt(9L, "2026-01-20T00:00:00Z")));

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1400);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("A-");
        assertThat(player.getDormantSince()).isEqualTo(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void capturesNewFloorOnlyWhenReturnedPlayerStartsAnotherDormancyEpisode() {
        Group group = group(1L);
        Player player = player(9L, group, 1320, "2026-01-02T00:00:00Z");
        player.setTier("B+");
        player.setHighestAchievedTier("A-");
        player.setDormantSince(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        player.setReturnedAt(OffsetDateTime.parse("2026-03-18T00:00:00Z"));
        player.setDormancyEpisodeFloorTier("A-");
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-03-01T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L))
            .thenReturn(List.of(lastPlayedAt(9L, "2026-03-18T00:00:00Z")));

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1290);
        assertThat(player.getDormancyEpisodeFloorTier()).isEqualTo("B");
        assertThat(player.getDormantSince()).isEqualTo(OffsetDateTime.parse("2026-04-02T00:00:00Z"));
        assertThat(player.getReturnedAt()).isNull();
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void clearsPreviousEpisodeFloorWhenNewDormancyStartsAtZeroMmr() {
        Group group = group(1L);
        Player player = player(9L, group, 0, "2026-01-02T00:00:00Z");
        player.setDormantSince(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        player.setReturnedAt(OffsetDateTime.parse("2026-03-18T00:00:00Z"));
        player.setDormancyEpisodeFloorTier("A-");
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-03-01T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L))
            .thenReturn(List.of(lastPlayedAt(9L, "2026-03-18T00:00:00Z")));

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isZero();
        assertThat(player.getDormancyEpisodeFloorTier()).isNull();
        assertThat(player.getDormantSince()).isEqualTo(OffsetDateTime.parse("2026-04-02T00:00:00Z"));
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void doesNotDemoteDTierBelowOneMmr() {
        Group group = group(1L);
        Player player = player(9L, group, 15, "2025-12-03T00:00:00Z");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1);
        assertThat(player.getTier()).isEqualTo("D");
    }

    @Test
    void keepsZeroMmrAtNoneForDormancyException() {
        Group group = group(1L);
        Player player = player(9L, group, 0, "2025-12-03T00:00:00Z");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isZero();
        assertThat(player.getTier()).isEqualTo("NONE");
    }

    private Group group(Long id) {
        Group group = new Group();
        group.setId(id);
        return group;
    }

    private Player player(Long id, Group group, int mmr, String createdAt) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname("로보");
        player.setRace("P");
        player.setMmr(mmr);
        player.setTier(com.balancify.backend.domain.PlayerTierPolicy.resolveTier(mmr));
        player.setCreatedAt(OffsetDateTime.parse(createdAt));
        return player;
    }

    private PlayerLastPlayedAtProjection lastPlayedAt(Long playerId, String playedAt) {
        return new PlayerLastPlayedAtProjection() {
            @Override
            public Long getPlayerId() {
                return playerId;
            }

            @Override
            public Instant getLastPlayedAt() {
                return OffsetDateTime.parse(playedAt).toInstant();
            }
        };
    }
}
