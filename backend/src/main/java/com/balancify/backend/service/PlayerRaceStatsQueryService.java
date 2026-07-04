package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerGameTypeStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatsResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerGameTypeStats;
import com.balancify.backend.domain.PlayerRaceStats;
import com.balancify.backend.repository.PlayerGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerMonthlyGameTypeStatsRepository;
import com.balancify.backend.repository.PlayerRaceStatsRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlayerRaceStatsQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> RACE_ORDER = List.of("P", "T", "Z", "PT", "PZ", "TZ", "PTZ");
    private static final List<String> GAME_TYPE_RACE_ORDER = List.of("P", "T", "Z", "PTZ");
    private static final Set<String> SUPPORTED_GAME_TYPES = Set.of("PP", "PT", "PZ", "PPP", "PPT", "PPZ", "PTZ");

    private final PlayerRepository playerRepository;
    private final PlayerRaceStatsRepository playerRaceStatsRepository;
    private final PlayerGameTypeStatsRepository playerGameTypeStatsRepository;
    private final PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository;
    private final GroupReadCacheService groupReadCacheService;
    private final Clock clock;

    @Autowired
    public PlayerRaceStatsQueryService(
        PlayerRepository playerRepository,
        PlayerRaceStatsRepository playerRaceStatsRepository,
        PlayerGameTypeStatsRepository playerGameTypeStatsRepository,
        PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this(
            playerRepository,
            playerRaceStatsRepository,
            playerGameTypeStatsRepository,
            playerMonthlyGameTypeStatsRepository,
            groupReadCacheService,
            Clock.system(KST)
        );
    }

    PlayerRaceStatsQueryService(
        PlayerRepository playerRepository,
        PlayerRaceStatsRepository playerRaceStatsRepository,
        PlayerGameTypeStatsRepository playerGameTypeStatsRepository,
        PlayerMonthlyGameTypeStatsRepository playerMonthlyGameTypeStatsRepository,
        GroupReadCacheService groupReadCacheService,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.playerRaceStatsRepository = playerRaceStatsRepository;
        this.playerGameTypeStatsRepository = playerGameTypeStatsRepository;
        this.playerMonthlyGameTypeStatsRepository = playerMonthlyGameTypeStatsRepository;
        this.groupReadCacheService = groupReadCacheService;
        this.clock = clock == null ? Clock.system(KST) : clock;
    }

    public List<GroupPlayerRaceStatsResponse> getGroupPlayerRaceStats(Long groupId) {
        String cacheKey = "player-race-stats:group:%d:".formatted(groupId);
        return groupReadCacheService.get(cacheKey, () -> List.copyOf(loadGroupPlayerRaceStats(groupId)));
    }

    public GroupPlayerRaceStatsResponse getGroupPlayerRaceStats(Long groupId, Long playerId) {
        String cacheKey = "player-race-stats:group:%d:player:%d".formatted(groupId, playerId);
        return groupReadCacheService.get(cacheKey, () -> loadGroupPlayerRaceStats(groupId, playerId));
    }

    public GroupPlayerRaceStatsResponse getGroupPlayerMonthlyRaceStats(Long groupId, Long playerId) {
        LocalDate statMonth = currentStatMonth();
        String cacheKey = "player-race-stats:group:%d:player:%d:month:%s".formatted(groupId, playerId, statMonth);
        return groupReadCacheService.get(cacheKey, () -> loadGroupPlayerMonthlyRaceStats(groupId, playerId, statMonth));
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

    private GroupPlayerRaceStatsResponse loadGroupPlayerMonthlyRaceStats(
        Long groupId,
        Long playerId,
        LocalDate statMonth
    ) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));
        List<GroupPlayerGameTypeStatResponse> byGameType = playerMonthlyGameTypeStatsRepository
            .findByGroupIdAndPlayerIdAndStatMonth(groupId, playerId, statMonth)
            .stream()
            .filter(stat -> isSupportedGameType(stat.getGameType()))
            .map(stat -> new GroupPlayerGameTypeStatResponse(
                stat.getGameType(),
                safeInt(stat.getWins()),
                safeInt(stat.getLosses()),
                safeInt(stat.getGames()),
                winRate(stat.getWins(), stat.getGames())
            ))
            .sorted(this::compareGameTypeStat)
            .toList();
        int wins = byGameType.stream().mapToInt(GroupPlayerGameTypeStatResponse::wins).sum();
        int losses = byGameType.stream().mapToInt(GroupPlayerGameTypeStatResponse::losses).sum();
        int games = wins + losses;

        return new GroupPlayerRaceStatsResponse(
            player.getId(),
            player.getNickname(),
            PlayerRacePolicy.toDisplayRace(player.getRace()),
            wins,
            losses,
            games,
            winRate(wins, games),
            List.of(),
            byGameType
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
            .filter(stat -> isSupportedGameType(stat.getGameType()))
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

    private LocalDate currentStatMonth() {
        return YearMonth.now(clock.withZone(KST)).atDay(1);
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

    private boolean isSupportedGameType(String gameType) {
        return SUPPORTED_GAME_TYPES.contains(gameType);
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
