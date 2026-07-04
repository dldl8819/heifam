package com.balancify.backend.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.balancify.backend.repository.PlayerGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerMonthlyGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerMonthlyStatsRepository;
import com.balancify.backend.repository.PlayerRaceStatsRepository;
import com.balancify.backend.repository.PlayerStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerStatsRefreshServiceTest {

    @Mock
    private PlayerStatsRepository playerStatsRepository;

    @Mock
    private PlayerRaceStatsRepository playerRaceStatsRepository;

    @Mock
    private PlayerGameTypeStatsRepository playerGameTypeStatsRepository;

    @Mock
    private PlayerMonthlyStatsRepository playerMonthlyStatsRepository;

    @Mock
    private PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository;

    private PlayerStatsRefreshService playerStatsRefreshService;

    @BeforeEach
    void setUp() {
        playerStatsRefreshService = new PlayerStatsRefreshService(
            playerStatsRepository,
            playerRaceStatsRepository,
            playerGameTypeStatsRepository,
            playerMonthlyStatsRepository,
            playerMonthlyGameTypeStatsRepository
        );
    }

    @Test
    void rebuildsBaseRaceAndGameTypeStatsForGroup() {
        playerStatsRefreshService.rebuildGroupStats(7L);

        verify(playerStatsRepository).rebuildGroupStats(7L);
        verify(playerRaceStatsRepository).deleteByGroupIdForRebuild(7L);
        verify(playerRaceStatsRepository).insertGroupRaceStats(7L);
        verify(playerGameTypeStatsRepository).deleteByGroupIdForRebuild(7L);
        verify(playerGameTypeStatsRepository).insertGroupGameTypeStats(7L);
        verify(playerMonthlyStatsRepository).deleteByGroupIdForRebuild(7L);
        verify(playerMonthlyStatsRepository).insertGroupMonthlyStats(7L);
        verify(playerMonthlyGameTypeStatsRepository).deleteByGroupIdForRebuild(7L);
        verify(playerMonthlyGameTypeStatsRepository).insertGroupMonthlyGameTypeStats(7L);
    }

    @Test
    void skipsRebuildWhenGroupIdIsMissing() {
        playerStatsRefreshService.rebuildGroupStats(null);

        verify(playerStatsRepository, never()).rebuildGroupStats(null);
        verify(playerRaceStatsRepository, never()).deleteByGroupIdForRebuild(null);
        verify(playerRaceStatsRepository, never()).insertGroupRaceStats(null);
        verify(playerGameTypeStatsRepository, never()).deleteByGroupIdForRebuild(null);
        verify(playerGameTypeStatsRepository, never()).insertGroupGameTypeStats(null);
        verify(playerMonthlyStatsRepository, never()).deleteByGroupIdForRebuild(null);
        verify(playerMonthlyStatsRepository, never()).insertGroupMonthlyStats(null);
        verify(playerMonthlyGameTypeStatsRepository, never()).deleteByGroupIdForRebuild(null);
        verify(playerMonthlyGameTypeStatsRepository, never()).insertGroupMonthlyGameTypeStats(null);
    }
}
