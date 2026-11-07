package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerMonthlyStats;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchParticipantRepository.PlayerMmrRangeProjection;
import com.balancify.backend.repository.PlayerMonthlyStatsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private static final Duration NEW_PLAYER_WINDOW = Duration.ofDays(14);

    private final GroupRosterCache groupRosterCache;
    private final PlayerMonthlyStatsRepository playerMonthlyStatsRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final GroupReadCacheService groupReadCacheService;
    private final Clock clock;

    @Autowired
    public RankingService(
        GroupRosterCache groupRosterCache,
        PlayerMonthlyStatsRepository playerMonthlyStatsRepository,
        MatchParticipantRepository matchParticipantRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this(
            groupRosterCache,
            playerMonthlyStatsRepository,
            matchParticipantRepository,
            groupReadCacheService,
            Clock.system(KST)
        );
    }

    RankingService(
        GroupRosterCache groupRosterCache,
        PlayerMonthlyStatsRepository playerMonthlyStatsRepository,
        MatchParticipantRepository matchParticipantRepository,
        GroupReadCacheService groupReadCacheService,
        Clock clock
    ) {
        this.groupRosterCache = groupRosterCache;
        this.playerMonthlyStatsRepository = playerMonthlyStatsRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.groupReadCacheService = groupReadCacheService;
        this.clock = clock == null ? Clock.system(KST) : clock;
    }

    public List<RankingItemResponse> getGroupRanking(Long groupId) {
        LocalDate statMonth = currentStatMonth();
        return List.copyOf(loadGroupRanking(groupId, statMonth));
    }

    private List<RankingItemResponse> loadGroupRanking(Long groupId, LocalDate statMonth) {
        // Dormant players (휴면) are off the ranking until an admin wakes them.
        List<Player> players = groupRosterCache.roster(groupId)
            .stream()
            .filter(PlayerRosterPolicy::isOnRoster)
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

        Map<Long, MmrRange> rangesByPlayerId = mmrRanges(groupId);

        OffsetDateTime newPlayerCutoff = OffsetDateTime.now(clock).minus(NEW_PLAYER_WINDOW);
        List<RankingCandidate> candidates = new ArrayList<>();
        for (Player player : players) {
            RankingStats stats = statsByPlayerId.getOrDefault(player.getId(), RankingStats.empty());
            int currentMmr = safeInt(player.getMmr());
            String currentTier = normalizeTier(PlayerTierPolicy.resolveTierForSnapshot(player.getTier()));

            candidates.add(new RankingCandidate(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                currentTier,
                currentMmr,
                stats,
                player.getCreatedAt() != null && player.getCreatedAt().isAfter(newPlayerCutoff),
                rangesByPlayerId.get(player.getId())
            ));
        }

        // Unassigned players sit at MMR 0, above everyone who has slid below zero, so they go last.
        candidates.sort(
            Comparator
                .comparing((RankingCandidate candidate) -> "UNASSIGNED".equals(candidate.tier()))
                .thenComparing(Comparator.comparingInt(RankingCandidate::mmr).reversed())
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
                stats.mmrDelta(),
                candidate.isNew(),
                candidate.peakMmr(),
                candidate.lowestMmr()
            ));
        }

        return ranking;
    }

    /**
     * Each player's MMR range over their matches. It changes only with a match result or an admin's
     * change to players, both of which empty the group's read cache, so it is kept there.
     */
    private Map<Long, MmrRange> mmrRanges(Long groupId) {
        return groupReadCacheService.get("ranking-mmr-range:group:" + groupId + ":", () -> {
            Map<Long, MmrRange> ranges = new HashMap<>();
            for (PlayerMmrRangeProjection row : matchParticipantRepository.findMmrRangesByGroupId(groupId)) {
                if (row.getPlayerId() != null && row.getPeakMmr() != null && row.getLowestMmr() != null) {
                    ranges.put(row.getPlayerId(), new MmrRange(row.getPeakMmr(), row.getLowestMmr()));
                }
            }
            return Map.copyOf(ranges);
        });
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

    private record MmrRange(int peak, int lowest) {
    }

    private record RankingCandidate(
        Long playerId,
        String nickname,
        String race,
        String tier,
        int mmr,
        RankingStats stats,
        boolean isNew,
        MmrRange matchRange
    ) {
        // The MMR now counts too: a change outside matches (an admin's edit, a tier change) can take
        // it past what the matches reached. No match yet, no range.
        Integer peakMmr() {
            return matchRange == null ? null : Math.max(matchRange.peak(), mmr);
        }

        Integer lowestMmr() {
            return matchRange == null ? null : Math.min(matchRange.lowest(), mmr);
        }
    }

}
