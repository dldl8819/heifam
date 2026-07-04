package com.balancify.backend.service;

import com.balancify.backend.repository.PlayerStatsRepository;
import com.balancify.backend.repository.PlayerGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerMonthlyGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerMonthlyStatsRepository;
import com.balancify.backend.repository.PlayerRaceStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerStatsRefreshService {

    private final PlayerStatsRepository playerStatsRepository;
    private final PlayerRaceStatsRepository playerRaceStatsRepository;
    private final PlayerGameTypeStatsRepository playerGameTypeStatsRepository;
    private final PlayerMonthlyStatsRepository playerMonthlyStatsRepository;
    private final PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository;

    public PlayerStatsRefreshService(
        PlayerStatsRepository playerStatsRepository,
        PlayerRaceStatsRepository playerRaceStatsRepository,
        PlayerGameTypeStatsRepository playerGameTypeStatsRepository,
        PlayerMonthlyStatsRepository playerMonthlyStatsRepository,
        PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository
    ) {
        this.playerStatsRepository = playerStatsRepository;
        this.playerRaceStatsRepository = playerRaceStatsRepository;
        this.playerGameTypeStatsRepository = playerGameTypeStatsRepository;
        this.playerMonthlyStatsRepository = playerMonthlyStatsRepository;
        this.playerMonthlyGameTypeStatsRepository = playerMonthlyGameTypeStatsRepository;
    }

    @Transactional
    public void rebuildGroupStats(Long groupId) {
        if (groupId == null) {
            return;
        }
        playerStatsRepository.rebuildGroupStats(groupId);
        playerRaceStatsRepository.deleteByGroupIdForRebuild(groupId);
        playerRaceStatsRepository.insertGroupRaceStats(groupId);
        playerGameTypeStatsRepository.deleteByGroupIdForRebuild(groupId);
        playerGameTypeStatsRepository.insertGroupGameTypeStats(groupId);
        playerMonthlyStatsRepository.deleteByGroupIdForRebuild(groupId);
        playerMonthlyStatsRepository.insertGroupMonthlyStats(groupId);
        playerMonthlyGameTypeStatsRepository.deleteByGroupIdForRebuild(groupId);
        playerMonthlyGameTypeStatsRepository.insertGroupMonthlyGameTypeStats(groupId);
    }
}
