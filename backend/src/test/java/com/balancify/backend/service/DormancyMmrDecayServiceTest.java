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
            100,
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

        assertThat(firstPlayer.getMmr()).isEqualTo(730);
        assertThat(secondPlayer.getMmr()).isEqualTo(680);
    }

    @Test
    void capsLongDormancyDecayAtTwoTierFloor() {
        Group group = group(1L);
        Player player = player(9L, group, 930, "2026-01-02T00:00:00Z");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(400);
        assertThat(player.getTier()).isEqualTo("B-");
        assertThat(player.getLastDormancyMmrDecayAt())
            .isEqualTo(OffsetDateTime.parse("2026-04-02T00:00:00Z"));
        assertThat(player.getDormantSince()).isEqualTo(OffsetDateTime.parse("2026-01-17T00:00:00Z"));
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

        assertThat(player.getMmr()).isEqualTo(830);
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void capsLongDormancyForATierPlayerAtBPlusFloor() {
        Group group = group(1L);
        Player player = player(9L, group, 1680, "2026-01-02T00:00:00Z");
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
        Player player = player(9L, group, 1320, "2026-01-02T00:00:00Z");
        player.setTier("B+");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(800);
        assertThat(player.getTier()).isEqualTo("B+");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void usesStoredTierInsteadOfLiveMmrTierForDormancyCap() {
        Group group = group(1L);
        Player player = player(9L, group, 1680, "2026-01-02T00:00:00Z");
        player.setTier("B+");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1080);
        assertThat(player.getTier()).isEqualTo("B+");
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void appliesConfiguredDormancyFloorTierWhenItIsHigherThanTwoStepCap() {
        Group group = group(1L);
        Player player = player(9L, group, 1930, "2026-01-02T00:00:00Z");
        player.setTier("A+");
        player.setDormancyMmrFloorTier("A");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of());

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(1600);
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
        player.setLastDormancyMmrDecayAt(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(player));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L))
            .thenReturn(List.of(lastPlayedAt(9L, "2026-03-20T00:00:00Z")));

        service.applyGroupDormancyDecay(1L);

        assertThat(player.getMmr()).isEqualTo(930);
        verify(playerRepository, never()).saveAll(anyList());
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
            public OffsetDateTime getLastPlayedAt() {
                return OffsetDateTime.parse(playedAt);
            }
        };
    }
}
