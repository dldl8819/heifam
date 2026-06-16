package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerStats;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerStatsRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStatsRepository playerStatsRepository;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(
            playerRepository,
            playerStatsRepository,
            new GroupReadCacheService(0)
        );
    }

    @Test
    void returnsRankingTierFromMonthlyTierWhileKeepingCurrentMmr() {
        Group group = new Group();
        group.setId(1L);
        Player player = player(1L, group, "PlayerAlpha", "P", "B-", 790);
        Player unassigned = player(2L, group, "PlayerBravo", "T", null, 0);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player, unassigned));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        List<RankingItemResponse> response = rankingService.getGroupRanking(1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).tier()).isEqualTo("B-");
        assertThat(response.get(0).currentMmr()).isEqualTo(790);
        assertThat(response.get(1).tier()).isEqualTo("UNASSIGNED");
        assertThat(response.get(1).currentMmr()).isEqualTo(0);
        verify(playerStatsRepository).findByGroupId(1L);
    }

    @Test
    void mapsRankingStatsFromPlayerStatsWithoutLoadingFullHistory() {
        Group group = new Group();
        group.setId(1L);
        Player player = player(1L, group, "PlayerAlpha", "P", "B-", 790);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of(rankingStats(1L, 3, 2, 15, "WWLWL", "W", 2)));

        List<RankingItemResponse> response = rankingService.getGroupRanking(1L);

        assertThat(response).hasSize(1);
        RankingItemResponse item = response.get(0);
        assertThat(item.wins()).isEqualTo(3);
        assertThat(item.losses()).isEqualTo(2);
        assertThat(item.games()).isEqualTo(5);
        assertThat(item.winRate()).isEqualTo(60.0);
        assertThat(item.last10()).isEqualTo("WWLWL");
        assertThat(item.streak()).isEqualTo("W2");
        assertThat(item.mmrDelta()).isEqualTo(15);
        verify(playerStatsRepository).findByGroupId(1L);
    }

    @Test
    void cachesRankingBrieflyToAvoidRepeatedDatabaseEgress() {
        GroupReadCacheService cache = new GroupReadCacheService(60_000);
        rankingService = new RankingService(playerRepository, playerStatsRepository, cache);

        Group group = new Group();
        group.setId(1L);
        Player player = player(1L, group, "PlayerAlpha", "P", "B-", 790);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player));
        when(playerStatsRepository.findByGroupId(1L))
            .thenReturn(List.of());

        rankingService.getGroupRanking(1L);
        rankingService.getGroupRanking(1L);

        verify(playerRepository, times(1)).findByGroup_IdOrderByMmrDescIdAsc(1L);
        verify(playerStatsRepository, times(1)).findByGroupId(1L);
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
        player.setActive(true);
        return player;
    }

    private PlayerStats rankingStats(
        Long playerId,
        int wins,
        int losses,
        int mmrDelta,
        String last10,
        String streakSymbol,
        int streakCount
    ) {
        PlayerStats stats = new PlayerStats();
        stats.setPlayerId(playerId);
        stats.setGroupId(1L);
        stats.setWins(wins);
        stats.setLosses(losses);
        stats.setGames(wins + losses);
        stats.setMmrDelta(mmrDelta);
        stats.setLast10(last10);
        stats.setStreakSymbol(streakSymbol);
        stats.setStreakCount(streakCount);
        return stats;
    }
}
