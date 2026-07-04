package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerMonthlyStats;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.PlayerMonthlyStatsRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RankingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PlayerRepository playerRepository;
    private final PlayerMonthlyStatsRepository playerMonthlyStatsRepository;
    private final GroupReadCacheService groupReadCacheService;
    private final Clock clock;

    @Autowired
    public RankingService(
        PlayerRepository playerRepository,
        PlayerMonthlyStatsRepository playerMonthlyStatsRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this(playerRepository, playerMonthlyStatsRepository, groupReadCacheService, Clock.system(KST));
    }

    RankingService(
        PlayerRepository playerRepository,
        PlayerMonthlyStatsRepository playerMonthlyStatsRepository,
        GroupReadCacheService groupReadCacheService,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.playerMonthlyStatsRepository = playerMonthlyStatsRepository;
        this.groupReadCacheService = groupReadCacheService;
        this.clock = clock == null ? Clock.system(KST) : clock;
    }

    public List<RankingItemResponse> getGroupRanking(Long groupId) {
        LocalDate statMonth = currentStatMonth();
        String cacheKey = "ranking:group:%d:month:%s".formatted(groupId, statMonth);
        return groupReadCacheService.get(cacheKey, () -> List.copyOf(loadGroupRanking(groupId, statMonth)));
    }

    private List<RankingItemResponse> loadGroupRanking(Long groupId, LocalDate statMonth) {
        List<Player> players = playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
            .stream()
            .filter(Player::isActive)
            .toList();
        if (players.isEmpty()) {
            return List.of();
        }

        Map<Long, RankingStats> statsByPlayerId = new HashMap<>();
        for (PlayerMonthlyStats stats : playerMonthlyStatsRepository.findByGroupIdAndStatMonth(groupId, statMonth)) {
            if (stats.getPlayerId() == null) {
                continue;
            }
            statsByPlayerId.put(stats.getPlayerId(), toRankingStats(stats));
        }

        List<RankingCandidate> candidates = new ArrayList<>();
        for (Player player : players) {
            RankingStats stats = statsByPlayerId.getOrDefault(player.getId(), RankingStats.empty());
            int currentMmr = safeInt(player.getMmr());
            String currentTier = normalizeTier(
                PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr)
            );

            candidates.add(new RankingCandidate(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                currentTier,
                currentMmr,
                stats
            ));
        }

        candidates.sort(
            Comparator
                .comparingInt(RankingCandidate::mmr)
                .reversed()
                .thenComparing(RankingCandidate::playerId, Comparator.nullsLast(Long::compareTo))
        );

        List<RankingItemResponse> ranking = new ArrayList<>();
        int rank = 1;
        for (RankingCandidate candidate : candidates) {
            RankingStats stats = candidate.stats();
            ranking.add(new RankingItemResponse(
                rank++,
                candidate.nickname(),
                candidate.race(),
                candidate.tier(),
                candidate.mmr(),
                stats.wins(),
                stats.losses(),
                stats.games(),
                stats.winRate(),
                stats.streak(),
                stats.last10(),
                stats.mmrDelta()
            ));
        }

        return ranking;
    }

    private LocalDate currentStatMonth() {
        return YearMonth.now(clock.withZone(KST)).atDay(1);
    }

    private RankingStats toRankingStats(PlayerMonthlyStats stats) {
        int wins = safeInt(stats.getWins());
        int losses = safeInt(stats.getLosses());
        int games = Math.max(0, safeInt(stats.getGames()));
        if (games != wins + losses) {
            games = wins + losses;
        }
        double winRate = games == 0 ? 0.0 : round2((wins * 100.0) / games);
        String streakSymbol = normalizeStreakSymbol(stats.getStreakSymbol());
        int streakCount = "N".equals(streakSymbol) ? 0 : safeInt(stats.getStreakCount());
        String streak = streakSymbol + streakCount;

        return new RankingStats(
            wins,
            losses,
            games,
            winRate,
            streak,
            stats.getLast10() == null ? "" : stats.getLast10(),
            safeInt(stats.getMmrDelta())
        );
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private String normalizeTier(String tier) {
        return "NONE".equals(tier) ? "UNASSIGNED" : tier;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeStreakSymbol(String value) {
        if ("W".equals(value) || "L".equals(value)) {
            return value;
        }
        return "N";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RankingStats(
        int wins,
        int losses,
        int games,
        double winRate,
        String streak,
        String last10,
        int mmrDelta
    ) {
        private static RankingStats empty() {
            return new RankingStats(0, 0, 0, 0.0, "N0", "", 0);
        }
    }

    private record RankingCandidate(
        Long playerId,
        String nickname,
        String race,
        String tier,
        int mmr,
        RankingStats stats
    ) {
    }

}
