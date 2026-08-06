package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTierBoardResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerStatsRepository;
import com.balancify.backend.domain.PlayerStats;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerQueryServiceTest {

    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-04-02T00:00:00Z");

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStatsRepository playerStatsRepository;

    private PlayerQueryService playerQueryService;

    @BeforeEach
    void setUp() {
        playerQueryService = new PlayerQueryService(
            playerRepository,
            playerStatsRepository,
            new GroupReadCacheService(0),
            Clock.fixed(Instant.parse("2026-07-12T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void returnsPlayersSortedByCurrentMmrWithStats() {
        Group group = new Group();
        group.setId(1L);

        Player p1 = player(1L, group, "Alpha", "P", "A", 1500);
        Player p2 = player(2L, group, "Bravo", "T", null, 1700);
        Player p3 = player(3L, group, "Charlie", "Z", "b+", 1600);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(p1, p2, p3));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of(resultStats(1L, 1, 1), resultStats(2L, 1, 1)));

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(3);

        GroupPlayerResponse first = response.get(0);
        assertThat(first.id()).isEqualTo(2L);
        assertThat(first.nickname()).isEqualTo("Bravo");
        assertThat(first.currentMmr()).isEqualTo(1700);
        assertThat(first.tier()).isEqualTo("A");
        assertThat(first.wins()).isEqualTo(1);
        assertThat(first.losses()).isEqualTo(1);
        assertThat(first.games()).isEqualTo(2);

        GroupPlayerResponse second = response.get(1);
        assertThat(second.id()).isEqualTo(3L);
        assertThat(second.tier()).isEqualTo("B+");
        assertThat(second.wins()).isZero();
        assertThat(second.losses()).isZero();
        assertThat(second.games()).isZero();

        GroupPlayerResponse third = response.get(2);
        assertThat(third.id()).isEqualTo(1L);
        assertThat(third.currentMmr()).isEqualTo(1500);
        assertThat(third.tier()).isEqualTo("A");
        assertThat(third.wins()).isEqualTo(1);
        assertThat(third.losses()).isEqualTo(1);
        assertThat(third.games()).isEqualTo(2);
    }

    @Test
    void returnsEmptyWhenGroupHasNoPlayers() {
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(99L)).thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(99L, false);
        assertThat(response).isEmpty();
    }

    @Test
    void returnsPersistedMmrAfterDormancyDecayHasRun() {
        Group group = new Group();
        group.setId(1L);

        Player robo = player(9L, group, "로보", "P", "A+", 920);
        robo.setCreatedAt(OffsetDateTime.parse("2026-02-20T00:00:00Z"));
        robo.setDormancyMmrFloorTier("B+");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(robo));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).tier()).isEqualTo("A+");
        assertThat(response.get(0).currentMmr()).isEqualTo(920);
        assertThat(response.get(0).dormancyMmrFloorTier()).isEqualTo("B+");
        assertThat(response.get(0).games()).isZero();
    }

    @Test
    void returnsMonthlySnapshotAndLiveTierFieldsForTierChangeNotice() {
        Group group = new Group();
        group.setId(1L);

        OffsetDateTime snapshotAt = OffsetDateTime.parse("2026-04-30T23:59:59+09:00");
        Player player = player(1L, group, "스냅샷", "P", "B", 1220);
        player.setLastTierSnapshotAt(snapshotAt);
        player.setLastTierSnapshotMmr(980);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(1);
        GroupPlayerResponse item = response.get(0);
        assertThat(item.tier()).isEqualTo("B");
        assertThat(item.lastTierSnapshotAt()).isEqualTo(snapshotAt);
        assertThat(item.lastTierSnapshotMmr()).isEqualTo(980);
        assertThat(item.lastTierSnapshotTier()).isEqualTo("B");
        assertThat(item.liveTier()).isEqualTo("B+");
        assertThat(item.currentMmr()).isEqualTo(1220);
    }

    @Test
    void returnsDTierForSnapshotAndLiveTierFields() {
        Group group = new Group();
        group.setId(1L);

        OffsetDateTime snapshotAt = OffsetDateTime.parse("2026-04-30T23:59:59+09:00");
        Player player = player(1L, group, "PlayerDelta", "P", "D", 150);
        player.setLastTierSnapshotAt(snapshotAt);
        player.setLastTierSnapshotMmr(150);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(1);
        GroupPlayerResponse item = response.get(0);
        assertThat(item.tier()).isEqualTo("D");
        assertThat(item.lastTierSnapshotMmr()).isEqualTo(150);
        assertThat(item.lastTierSnapshotTier()).isEqualTo("D");
        assertThat(item.liveTier()).isEqualTo("D");
    }

    @Test
    void returnsTierBoardUsingMonthlyTierAndLiveTierSeparately() {
        Group group = new Group();
        group.setId(1L);

        Player first = player(1L, group, "PlayerAlpha", "P", "B-", 790);
        Player second = player(2L, group, "PlayerBravo", "T", "A", 1810);
        Player inactive = player(3L, group, "Charlie", "Z", "A", 1900);
        inactive.setActive(false);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(inactive, first, second));

        List<GroupPlayerTierBoardResponse> response = playerQueryService.getGroupPlayerTierBoard(1L);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(GroupPlayerTierBoardResponse::nickname)
            .containsExactly("PlayerBravo", "PlayerAlpha");
        assertThat(response.get(0).tier()).isEqualTo("A");
        assertThat(response.get(0).liveTier()).isEqualTo("A+");
        assertThat(response.get(1).tier()).isEqualTo("B-");
        assertThat(response.get(1).liveTier()).isEqualTo("C+");
        verifyNoInteractions(playerStatsRepository);
    }

    @Test
    void omitsMonthlySnapshotFieldsWhenSnapshotMmrIsMissing() {
        Group group = new Group();
        group.setId(1L);

        Player player = player(1L, group, "스냅샷없음", "P", "B", 1220);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(1);
        GroupPlayerResponse item = response.get(0);
        assertThat(item.lastTierSnapshotAt()).isNull();
        assertThat(item.lastTierSnapshotMmr()).isNull();
        assertThat(item.lastTierSnapshotTier()).isNull();
        assertThat(item.liveTier()).isEqualTo("B+");
    }

    @Test
    void doesNotDemoteDormantTierWhenParticipationIsAboveThreshold() {
        Group group = new Group();
        group.setId(1L);

        Player robo = player(9L, group, "로보", "P", "A+", 930);
        robo.setCreatedAt(OffsetDateTime.parse("2026-03-20T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(robo));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of(resultStats(9L, 4, 0)));

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).tier()).isEqualTo("A+");
        assertThat(response.get(0).games()).isEqualTo(4);
    }

    @Test
    void excludesInactivePlayersByDefault() {
        Group group = new Group();
        group.setId(1L);

        Player active = player(1L, group, "활성", "P", "A", 1500);
        Player inactive = player(2L, group, "비활성", "T", "A", 1700);
        inactive.setActive(false);
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        inactive.setChatLeftAt(chatLeftAt);
        inactive.setChatLeftReason("톡방 퇴장");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(inactive, active));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).extracting(GroupPlayerResponse::nickname).containsExactly("활성");
        assertThat(response.get(0).active()).isTrue();
    }

    @Test
    void returnsRetainedInactiveIdentityAndStatsOnlyWhenRequestedByAdmin() {
        Group group = new Group();
        group.setId(1L);

        Player active = player(1L, group, "활성", "P", "A", 1500);
        Player inactive = player(2L, group, "비활성", "T", "A", 1700);
        inactive.setActive(false);
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        inactive.setChatLeftAt(chatLeftAt);
        inactive.setChatLeftReason("톡방 퇴장");
        inactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        inactive.setIdentityRetainedUntil(OffsetDateTime.parse("2031-05-02T12:41:00+09:00"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(inactive, active));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of(resultStats(2L, 2, 1)));

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, true);

        assertThat(response).hasSize(2);
        assertThat(response.getFirst().active()).isTrue();

        GroupPlayerResponse retainedInactive = response.get(1);
        assertThat(retainedInactive.id()).isEqualTo(2L);
        assertThat(retainedInactive.nickname()).isNotEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(retainedInactive.active()).isFalse();
        assertThat(retainedInactive.race()).isEqualTo("T");
        assertThat(retainedInactive.tier()).isNull();
        assertThat(retainedInactive.currentMmr()).isNull();
        assertThat(retainedInactive.liveTier()).isNull();
        assertThat(retainedInactive.wins()).isEqualTo(2);
        assertThat(retainedInactive.losses()).isEqualTo(1);
        assertThat(retainedInactive.games()).isEqualTo(3);
        assertThat(retainedInactive.chatLeftAt()).isEqualTo(chatLeftAt);
        assertThat(retainedInactive.chatLeftReason()).isNotBlank();
        assertThat(retainedInactive.chatRejoinedAt()).isNull();
        assertThat(retainedInactive.tierChangeAcknowledgedTier()).isNull();
        assertThat(retainedInactive.tierChangeAcknowledgedAt()).isNull();
        assertThat(retainedInactive.dormancyMmrFloorTier()).isNull();
        assertThat(retainedInactive.lifecycleStatus()).isEqualTo("INACTIVE");
        assertThat(retainedInactive.identityRetainedUntil())
            .isEqualTo(OffsetDateTime.parse("2031-05-02T12:41:00+09:00"));
    }

    @Test
    void masksExpiredInactiveIdentityEvenBeforeRetentionSweepRuns() {
        Group group = new Group();
        group.setId(1L);
        Player inactive = player(2L, group, "EXPIRED_NICKNAME", "T", "A", 1700);
        inactive.setActive(false);
        inactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        inactive.setIdentityRetainedUntil(OffsetDateTime.parse("2026-07-12T02:59:59Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(inactive));
        when(playerStatsRepository.findByGroupId(1L)).thenReturn(List.of(resultStats(2L, 2, 1)));

        GroupPlayerResponse response = playerQueryService.getGroupPlayers(1L, true).getFirst();

        assertThat(response.id()).isNull();
        assertThat(response.nickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(response.race()).isNull();
        assertThat(response.wins()).isZero();
        assertThat(response.chatLeftReason()).isNull();
        assertThat(response.lifecycleStatus()).isNull();
    }

    @Test
    void bypassesReadCacheForAdministrativeInactiveRoster() {
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of());

        playerQueryService.getGroupPlayers(1L, true);
        playerQueryService.getGroupPlayers(1L, true);

        verify(playerRepository, times(2)).findByGroup_IdOrderByMmrDescIdAsc(1L);
    }

    @Test
    void sortsRetainedInactiveRowsByAllowedIdentityInsteadOfHiddenMmr() {
        Group group = new Group();
        group.setId(1L);
        OffsetDateTime retainedUntil = OffsetDateTime.parse("2031-07-12T03:00:00Z");
        Player highMmr = player(2L, group, "Zulu", "T", "A", 1900);
        highMmr.setActive(false);
        highMmr.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        highMmr.setIdentityRetainedUntil(retainedUntil);
        Player lowMmr = player(3L, group, "Alpha", "P", "B", 900);
        lowMmr.setActive(false);
        lowMmr.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        lowMmr.setIdentityRetainedUntil(retainedUntil);
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(highMmr, lowMmr));
        when(playerStatsRepository.findByGroupId(1L)).thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, true);

        assertThat(response).extracting(GroupPlayerResponse::nickname)
            .containsExactly("Alpha", "Zulu");
        assertThat(response).extracting(GroupPlayerResponse::currentMmr)
            .containsOnlyNulls();
    }

    @Test
    void includesChatRejoinedAtForReactivatedPlayers() {
        Group group = new Group();
        group.setId(1L);

        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        OffsetDateTime chatRejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");
        Player reactivated = player(1L, group, "복귀", "P", "A", 1500);
        reactivated.setChatLeftAt(chatLeftAt);
        reactivated.setChatLeftReason("톡방 퇴장");
        reactivated.setChatRejoinedAt(chatRejoinedAt);
        reactivated.setTierChangeAcknowledgedTier("A+");
        reactivated.setTierChangeAcknowledgedAt(chatRejoinedAt);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(reactivated));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L, false);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).active()).isTrue();
        assertThat(response.get(0).chatLeftAt()).isEqualTo(chatLeftAt);
        assertThat(response.get(0).chatLeftReason()).isEqualTo("톡방 퇴장");
        assertThat(response.get(0).chatRejoinedAt()).isEqualTo(chatRejoinedAt);
        assertThat(response.get(0).tierChangeAcknowledgedTier()).isEqualTo("A+");
        assertThat(response.get(0).tierChangeAcknowledgedAt()).isEqualTo(chatRejoinedAt);
    }

    private Player player(
        Long id,
        Group group,
        String nickname,
        String race,
        String tier,
        int mmr
    ) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setRace(race);
        player.setTier(tier);
        player.setMmr(mmr);
        player.setCreatedAt(FIXED_NOW);
        return player;
    }

    private PlayerStats resultStats(Long playerId, int wins, int losses) {
        PlayerStats stats = new PlayerStats();
        stats.setPlayerId(playerId);
        stats.setGroupId(1L);
        stats.setWins(wins);
        stats.setLosses(losses);
        stats.setGames(wins + losses);
        stats.setMmrDelta(0);
        return stats;
    }
}
