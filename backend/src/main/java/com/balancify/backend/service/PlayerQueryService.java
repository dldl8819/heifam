package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTierBoardResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerStats;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerStatsRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlayerQueryService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final GroupReadCacheService groupReadCacheService;

    @Autowired
    public PlayerQueryService(
        PlayerRepository playerRepository,
        PlayerStatsRepository playerStatsRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this.playerRepository = playerRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.groupReadCacheService = groupReadCacheService;
    }

    public List<GroupPlayerResponse> getGroupPlayers(Long groupId, boolean includeInactive) {
        String cacheKey = "players:group:%d:includeInactive:%s".formatted(groupId, includeInactive);
        return groupReadCacheService.get(cacheKey, () -> List.copyOf(loadGroupPlayers(groupId, includeInactive)));
    }

    private List<GroupPlayerResponse> loadGroupPlayers(Long groupId, boolean includeInactive) {
        List<Player> players = new ArrayList<>(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
            .stream()
            .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player))
            .toList());
        if (players.isEmpty()) {
            return List.of();
        }

        players.sort(
            Comparator
                .comparingInt((Player player) -> safeInt(player.getMmr()))
                .reversed()
                .thenComparing(Player::getId, Comparator.nullsLast(Long::compareTo))
        );

        Map<Long, StatsAccumulator> statsByPlayerId = new HashMap<>();
        for (PlayerStats stats : playerStatsRepository.findByGroupId(groupId)) {
            if (stats.getPlayerId() == null) {
                continue;
            }
            statsByPlayerId.put(stats.getPlayerId(), new StatsAccumulator(
                safeInt(stats.getWins()),
                safeInt(stats.getLosses())
            ));
        }

        List<GroupPlayerResponse> responses = new ArrayList<>();
        for (Player player : players) {
            StatsAccumulator stats =
                statsByPlayerId.getOrDefault(player.getId(), new StatsAccumulator(0, 0));
            int games = stats.wins() + stats.losses();
            Integer baseMmr = player.getBaseMmr();
            String baseTier = baseMmr == null ? null : PlayerTierPolicy.resolveTier(baseMmr);
            int currentMmr = safeInt(player.getMmr());
            String currentTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);
            Integer lastTierSnapshotMmr = player.getLastTierSnapshotMmr();
            String lastTierSnapshotTier = lastTierSnapshotMmr == null
                ? null
                : currentTier;
            String liveTier = PlayerTierPolicy.resolveTier(currentMmr);

            responses.add(new GroupPlayerResponse(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                currentTier,
                baseMmr,
                baseTier,
                currentMmr,
                player.getLastTierSnapshotAt(),
                lastTierSnapshotMmr,
                lastTierSnapshotTier,
                liveTier,
                stats.wins(),
                stats.losses(),
                games,
                player.isActive(),
                player.getChatLeftAt(),
                player.getChatLeftReason(),
                player.getChatRejoinedAt(),
                player.getTierChangeAcknowledgedTier(),
                player.getTierChangeAcknowledgedAt(),
                PlayerTierPolicy.normalizeRankedTier(player.getDormancyMmrFloorTier()).isEmpty()
                    ? null
                    : PlayerTierPolicy.normalizeRankedTier(player.getDormancyMmrFloorTier())
            ));
        }

        return responses;
    }

    public List<GroupPlayerTierBoardResponse> getGroupPlayerTierBoard(Long groupId) {
        String cacheKey = "players-tier-board:group:%d:".formatted(groupId);
        return groupReadCacheService.get(cacheKey, () -> List.copyOf(loadGroupPlayerTierBoard(groupId)));
    }

    private List<GroupPlayerTierBoardResponse> loadGroupPlayerTierBoard(Long groupId) {
        List<Player> players = new ArrayList<>(
            playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
                .stream()
                .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player))
                .toList()
        );
        players.sort(
            Comparator
                .comparingInt((Player player) -> safeInt(player.getMmr()))
                .reversed()
                .thenComparing(Player::getId, Comparator.nullsLast(Long::compareTo))
        );

        return players
            .stream()
            .map(player -> {
                int currentMmr = safeInt(player.getMmr());
                String monthlyTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);
                String liveTier = PlayerTierPolicy.resolveTier(currentMmr);
                return new GroupPlayerTierBoardResponse(
                    player.getId(),
                    player.getNickname(),
                    normalizeRace(player.getRace()),
                    monthlyTier,
                    liveTier,
                    player.isActive()
                );
            })
            .toList();
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record StatsAccumulator(int wins, int losses) {
    }

}
