package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerRaceStatsResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerGameTypeStats;
import com.balancify.backend.domain.PlayerMonthlyGameTypeStats;
import com.balancify.backend.domain.PlayerRaceStats;
import com.balancify.backend.repository.PlayerGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerMonthlyGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerRaceStatsRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerRaceStatsQueryServiceTest {

    private static final LocalDate JULY_2026 = LocalDate.of(2026, 7, 1);
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-04T15:00:00Z"),
        ZoneId.of("Asia/Seoul")
    );

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerRaceStatsRepository playerRaceStatsRepository;

    @Mock
    private PlayerGameTypeStatsRepository playerGameTypeStatsRepository;

    @Mock
    private PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository;

    private PlayerRaceStatsQueryService playerRaceStatsQueryService;

    @BeforeEach
    void setUp() {
        playerRaceStatsQueryService = new PlayerRaceStatsQueryService(
            playerRepository,
            playerRaceStatsRepository,
            playerGameTypeStatsRepository,
            playerMonthlyGameTypeStatsRepository,
            new GroupReadCacheService(30_000),
            FIXED_CLOCK
        );
    }

    @Test
    void returnsPlayerRaceAndGameTypeStatsFromAggregateTables() {
        Group group = new Group();
        group.setId(1L);
        Player alpha = player(1L, group, "Alpha", "PT", 1500, true);
        Player bravo = player(2L, group, "Bravo", "Z", 1400, true);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(alpha, bravo));
        when(playerRaceStatsRepository.findByGroupId(1L))
            .thenReturn(List.of(
                raceStats(1L, 1L, "P", 3, 1),
                raceStats(1L, 1L, "T", 1, 1),
                raceStats(2L, 1L, "Z", 0, 2)
            ));
        when(playerGameTypeStatsRepository.findByGroupId(1L))
            .thenReturn(List.of(
                gameTypeStats(1L, 1L, "PPT", 2, 1),
                gameTypeStats(1L, 1L, "PTZ", 2, 1),
                gameTypeStats(2L, 1L, "PTZ", 0, 2)
            ));

        List<GroupPlayerRaceStatsResponse> response =
            playerRaceStatsQueryService.getGroupPlayerRaceStats(1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).nickname()).isEqualTo("Alpha");
        assertThat(response.get(0).race()).isEqualTo("PT");
        assertThat(response.get(0).wins()).isEqualTo(4);
        assertThat(response.get(0).losses()).isEqualTo(2);
        assertThat(response.get(0).games()).isEqualTo(6);
        assertThat(response.get(0).winRate()).isEqualTo(66.67);
        assertThat(response.get(0).byRace())
            .extracting("race")
            .containsExactly("P", "T");
        assertThat(response.get(0).byGameType())
            .extracting("gameType")
            .containsExactly("PPT", "PTZ");

        assertThat(response.get(1).nickname()).isEqualTo("Bravo");
        assertThat(response.get(1).winRate()).isEqualTo(0.0);
        assertThat(response.get(1).byRace()).hasSize(1);
    }

    @Test
    void excludesInactivePlayersAndCachesGroupRead() {
        Group group = new Group();
        group.setId(1L);
        Player active = player(1L, group, "Active", "P", 1500, true);
        Player inactive = player(2L, group, "Inactive", "T", 1400, false);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(active, inactive));
        when(playerRaceStatsRepository.findByGroupId(1L)).thenReturn(List.of());
        when(playerGameTypeStatsRepository.findByGroupId(1L)).thenReturn(List.of());

        List<GroupPlayerRaceStatsResponse> first =
            playerRaceStatsQueryService.getGroupPlayerRaceStats(1L);
        List<GroupPlayerRaceStatsResponse> second =
            playerRaceStatsQueryService.getGroupPlayerRaceStats(1L);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.get(0).nickname()).isEqualTo("Active");
        verify(playerRepository, times(1)).findByGroup_IdOrderByMmrDescIdAsc(1L);
        verify(playerRaceStatsRepository, times(1)).findByGroupId(1L);
        verify(playerGameTypeStatsRepository, times(1)).findByGroupId(1L);
    }

    @Test
    void returnsSinglePlayerStatsFromAggregateTables() {
        Group group = new Group();
        group.setId(1L);
        Player alpha = player(1L, group, "Alpha", "PT", 1500, true);

        when(playerRepository.findByIdAndGroup_Id(1L, 1L))
            .thenReturn(java.util.Optional.of(alpha));
        when(playerRaceStatsRepository.findByGroupIdAndPlayerId(1L, 1L))
            .thenReturn(List.of(
                raceStats(1L, 1L, "P", 3, 1),
                raceStats(1L, 1L, "T", 1, 1)
            ));
        when(playerGameTypeStatsRepository.findByGroupIdAndPlayerId(1L, 1L))
            .thenReturn(List.of(
                gameTypeStats(1L, 1L, "PPP", 1, 0),
                gameTypeStats(1L, 1L, "PPT", 2, 1),
                gameTypeStats(1L, 1L, "PPZ", 1, 1),
                gameTypeStats(1L, 1L, "PTZ", 0, 1),
                gameTypeStats(1L, 1L, "PP", 1, 0),
                gameTypeStats(1L, 1L, "PPTZPTZ", 9, 0)
            ));

        GroupPlayerRaceStatsResponse response =
            playerRaceStatsQueryService.getGroupPlayerRaceStats(1L, 1L);

        assertThat(response.nickname()).isEqualTo("Alpha");
        assertThat(response.wins()).isEqualTo(4);
        assertThat(response.losses()).isEqualTo(2);
        assertThat(response.byGameType())
            .extracting("gameType")
            .containsExactlyInAnyOrder("PPP", "PPT", "PPZ", "PTZ", "PP");
        verify(playerRepository, never()).findByGroup_IdOrderByMmrDescIdAsc(1L);
        verify(playerRaceStatsRepository, never()).findByGroupId(1L);
        verify(playerGameTypeStatsRepository, never()).findByGroupId(1L);
    }

    @Test
    void returnsSinglePlayerMonthlyGameTypeStatsForRankingModal() {
        Group group = new Group();
        group.setId(1L);
        Player alpha = player(1L, group, "Alpha", "PT", 1500, true);

        when(playerRepository.findByIdAndGroup_Id(1L, 1L))
            .thenReturn(java.util.Optional.of(alpha));
        when(playerMonthlyGameTypeStatsRepository.findByGroupIdAndPlayerIdAndStatMonth(1L, 1L, JULY_2026))
            .thenReturn(List.of(
                monthlyGameTypeStats(1L, 1L, JULY_2026, "PPP", 1, 0),
                monthlyGameTypeStats(1L, 1L, JULY_2026, "PPT", 2, 1),
                monthlyGameTypeStats(1L, 1L, JULY_2026, "PT", 1, 1),
                monthlyGameTypeStats(1L, 1L, JULY_2026, "PTZPTZPTZ", 3, 0)
            ));

        GroupPlayerRaceStatsResponse response =
            playerRaceStatsQueryService.getGroupPlayerMonthlyRaceStats(1L, 1L);

        assertThat(response.nickname()).isEqualTo("Alpha");
        assertThat(response.wins()).isEqualTo(4);
        assertThat(response.losses()).isEqualTo(2);
        assertThat(response.games()).isEqualTo(6);
        assertThat(response.byRace()).isEmpty();
        assertThat(response.byGameType())
            .extracting("gameType")
            .containsExactly("PPT", "PT", "PPP");
        verify(playerRaceStatsRepository, never()).findByGroupIdAndPlayerId(1L, 1L);
        verify(playerGameTypeStatsRepository, never()).findByGroupIdAndPlayerId(1L, 1L);
    }

    private Player player(Long id, Group group, String nickname, String race, int mmr, boolean active) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setRace(race);
        player.setMmr(mmr);
        player.setActive(active);
        return player;
    }

    private PlayerRaceStats raceStats(Long playerId, Long groupId, String race, int wins, int losses) {
        PlayerRaceStats stats = new PlayerRaceStats();
        stats.setPlayerId(playerId);
        stats.setGroupId(groupId);
        stats.setRace(race);
        stats.setWins(wins);
        stats.setLosses(losses);
        stats.setGames(wins + losses);
        return stats;
    }

    private PlayerGameTypeStats gameTypeStats(Long playerId, Long groupId, String gameType, int wins, int losses) {
        PlayerGameTypeStats stats = new PlayerGameTypeStats();
        stats.setPlayerId(playerId);
        stats.setGroupId(groupId);
        stats.setGameType(gameType);
        stats.setWins(wins);
        stats.setLosses(losses);
        stats.setGames(wins + losses);
        return stats;
    }

    private PlayerMonthlyGameTypeStats monthlyGameTypeStats(
        Long playerId,
        Long groupId,
        LocalDate statMonth,
        String gameType,
        int wins,
        int losses
    ) {
        PlayerMonthlyGameTypeStats stats = new PlayerMonthlyGameTypeStats();
        stats.setPlayerId(playerId);
        stats.setGroupId(groupId);
        stats.setStatMonth(statMonth);
        stats.setGameType(gameType);
        stats.setWins(wins);
        stats.setLosses(losses);
        stats.setGames(wins + losses);
        return stats;
    }
}
