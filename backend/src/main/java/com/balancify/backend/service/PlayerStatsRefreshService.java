package com.balancify.backend.service;

import com.balancify.backend.repository.PlayerStatsRepository;
import com.balancify.backend.repository.PlayerGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerRaceStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerStatsRefreshService {

    private final PlayerStatsRepository playerStatsRepository;
    private final PlayerRaceStatsRepository playerRaceStatsRepository;
    private final PlayerGameTypeStatsRepository playerGameTypeStatsRepository;

    public PlayerStatsRefreshService(
        PlayerStatsRepository playerStatsRepository,
        PlayerRaceStatsRepository playerRaceStatsRepository,
        PlayerGameTypeStatsRepository playerGameTypeStatsRepository
    ) {
        this.playerStatsRepository = playerStatsRepository;
        this.playerRaceStatsRepository = playerRaceStatsRepository;
        this.playerGameTypeStatsRepository = playerGameTypeStatsRepository;
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
    }
}
