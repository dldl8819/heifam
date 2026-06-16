package com.balancify.backend.service;

import com.balancify.backend.repository.PlayerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerStatsRefreshService {

    private final PlayerStatsRepository playerStatsRepository;

    public PlayerStatsRefreshService(PlayerStatsRepository playerStatsRepository) {
        this.playerStatsRepository = playerStatsRepository;
    }

    @Transactional
    public void rebuildGroupStats(Long groupId) {
        if (groupId == null) {
            return;
        }
        playerStatsRepository.rebuildGroupStats(groupId);
    }
}
