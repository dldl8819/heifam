package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerGameTypeStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatsResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerGameTypeStats;
import com.balancify.backend.domain.PlayerRaceStats;
import com.balancify.backend.repository.PlayerGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerRaceStatsRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class PlayerRaceStatsQueryService {

    private static final List<String> RACE_ORDER = List.of("P", "T", "Z", "PT", "PZ", "TZ", "PTZ");
    private static final List<String> GAME_TYPE_RACE_ORDER = List.of("P", "T", "Z", "PTZ");

    private final PlayerRepository playerRepository;
    private final PlayerRaceStatsRepository playerRaceStatsRepository;
    private final PlayerGameTypeStatsRepository playerGameTypeStatsRepository;
    private final GroupReadCacheService groupReadCacheService;

    public PlayerRaceStatsQueryService(
        PlayerRepository playerRepository,
        PlayerRaceStatsRepository playerRaceStatsRepository,
        PlayerGameTypeStatsRepository playerGameTypeStatsRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this.playerRepository = playerRepository;
        this.playerRaceStatsRepository = playerRaceStatsRepository;
        this.playerGameTypeStatsRepository = playerGameTypeStatsRepository;
        this.groupReadCacheService = groupReadCacheService;
    }

    public List<GroupPlayerRaceStatsResponse> getGroupPlayerRaceStats(Long groupId) {
        String cacheKey = "player-race-stats:group:%d:".formatted(groupId);
        return groupReadCacheService.get(cacheKey, () -> List.copyOf(loadGroupPlayerRaceStats(groupId)));
    }

    public GroupPlayerRaceStatsResponse getGroupPlayerRaceStats(Long groupId, Long playerId) {
        String cacheKey = "player-race-stats:group:%d:player:%d".formatted(groupId, playerId);
        return groupReadCacheService.get(cacheKey, () -> loadGroupPlayerRaceStats(groupId, playerId));
    }

    private GroupPlayerRaceStatsResponse loadGroupPlayerRaceStats(Long groupId, Long playerId) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));

        return toResponse(
            player,
            playerRaceStatsRepository.findByGroupIdAndPlayerId(groupId, playerId),
            playerGameTypeStatsRepository.findByGroupIdAndPlayerId(groupId, playerId)
        );
    }

    private List<GroupPlayerRaceStatsResponse> loadGroupPlayerRaceStats(Long groupId) {
        List<Player> players = playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
            .stream()
            .filter(Player::isActive)
            .toList();
        Map<Long, List<PlayerRaceStats>> raceStatsByPlayerId = groupByPlayerId(
            playerRaceStatsRepository.findByGroupId(groupId)
        );
        Map<Long, List<PlayerGameTypeStats>> gameTypeStatsByPlayerId = groupGameTypeStatsByPlayerId(
            playerGameTypeStatsRepository.findByGroupId(groupId)
        );

        return players
            .stream()
            .map(player -> toResponse(
                player,
                raceStatsByPlayerId.getOrDefault(player.getId(), List.of()),
                gameTypeStatsByPlayerId.getOrDefault(player.getId(), List.of())
            ))
            .toList();
    }

    private Map<Long, List<PlayerRaceStats>> groupByPlayerId(List<PlayerRaceStats> stats) {
        Map<Long, List<PlayerRaceStats>> grouped = new HashMap<>();
        for (PlayerRaceStats stat : stats) {
            grouped.computeIfAbsent(stat.getPlayerId(), ignored -> new java.util.ArrayList<>()).add(stat);
        }
        return grouped;
    }

    private Map<Long, List<PlayerGameTypeStats>> groupGameTypeStatsByPlayerId(List<PlayerGameTypeStats> stats) {
        Map<Long, List<PlayerGameTypeStats>> grouped = new HashMap<>();
        for (PlayerGameTypeStats stat : stats) {
            grouped.computeIfAbsent(stat.getPlayerId(), ignored -> new java.util.ArrayList<>()).add(stat);
        }
        return grouped;
    }

    private GroupPlayerRaceStatsResponse toResponse(
        Player player,
        List<PlayerRaceStats> raceStats,
        List<PlayerGameTypeStats> gameTypeStats
    ) {
        List<GroupPlayerRaceStatResponse> byRace = raceStats
            .stream()
            .map(stat -> new GroupPlayerRaceStatResponse(
                stat.getRace(),
                safeInt(stat.getWins()),
                safeInt(stat.getLosses()),
                safeInt(stat.getGames()),
                winRate(stat.getWins(), stat.getGames())
            ))
            .sorted(this::compareRaceStat)
            .toList();

        List<GroupPlayerGameTypeStatResponse> byGameType = gameTypeStats
            .stream()
            .map(stat -> new GroupPlayerGameTypeStatResponse(
                stat.getGameType(),
                safeInt(stat.getWins()),
                safeInt(stat.getLosses()),
                safeInt(stat.getGames()),
                winRate(stat.getWins(), stat.getGames())
            ))
            .sorted(this::compareGameTypeStat)
            .toList();

        int wins = byRace.stream().mapToInt(GroupPlayerRaceStatResponse::wins).sum();
        int losses = byRace.stream().mapToInt(GroupPlayerRaceStatResponse::losses).sum();
        int games = wins + losses;

        return new GroupPlayerRaceStatsResponse(
            player.getId(),
            player.getNickname(),
            PlayerRacePolicy.toDisplayRace(player.getRace()),
            wins,
            losses,
            games,
            winRate(wins, games),
            byRace,
            byGameType
        );
    }

    private int compareRaceStat(GroupPlayerRaceStatResponse left, GroupPlayerRaceStatResponse right) {
        if (right.games() != left.games()) {
            return Integer.compare(right.games(), left.games());
        }
        if (right.wins() != left.wins()) {
            return Integer.compare(right.wins(), left.wins());
        }
        int leftOrder = raceOrder(left.race());
        int rightOrder = raceOrder(right.race());
        if (leftOrder != rightOrder) {
            return Integer.compare(leftOrder, rightOrder);
        }
        return left.race().compareTo(right.race());
    }

    private int compareGameTypeStat(GroupPlayerGameTypeStatResponse left, GroupPlayerGameTypeStatResponse right) {
        if (right.games() != left.games()) {
            return Integer.compare(right.games(), left.games());
        }
        if (right.wins() != left.wins()) {
            return Integer.compare(right.wins(), left.wins());
        }
        int gameTypeComparison = compareGameType(left.gameType(), right.gameType());
        if (gameTypeComparison != 0) {
            return gameTypeComparison;
        }
        return left.gameType().compareTo(right.gameType());
    }

    private int raceOrder(String race) {
        int index = RACE_ORDER.indexOf(race);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private int compareGameType(String left, String right) {
        int minLength = Math.min(left.length(), right.length());
        for (int index = 0; index < minLength; index++) {
            int leftOrder = gameTypeRaceOrder(String.valueOf(left.charAt(index)));
            int rightOrder = gameTypeRaceOrder(String.valueOf(right.charAt(index)));
            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }
        }
        if (left.length() != right.length()) {
            return Integer.compare(left.length(), right.length());
        }
        return 0;
    }

    private int gameTypeRaceOrder(String race) {
        int index = GAME_TYPE_RACE_ORDER.indexOf(race);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private double winRate(Integer wins, Integer games) {
        return winRate(safeInt(wins), safeInt(games));
    }

    private double winRate(int wins, int games) {
        if (games <= 0) {
            return 0.0;
        }
        return Math.round((wins * 10000.0) / games) / 100.0;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
