package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RankingService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final boolean dormancyEnabled;
    private final int dormancyInactiveDays;
    private final int dormancyDemoteSteps;
    private final Clock clock;

    public RankingService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        @Value("${balancify.rank.dormancy.enabled:true}") boolean dormancyEnabled,
        @Value("${balancify.rank.dormancy.inactive-days:30}") int dormancyInactiveDays,
        @Value("${balancify.rank.dormancy.demote-steps:1}") int dormancyDemoteSteps
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.dormancyEnabled = dormancyEnabled;
        this.dormancyInactiveDays = Math.max(0, dormancyInactiveDays);
        this.dormancyDemoteSteps = Math.max(0, dormancyDemoteSteps);
        this.clock = Clock.systemUTC();
    }

    public List<RankingItemResponse> getGroupRanking(Long groupId) {
        List<Player> players = playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
            .stream()
            .filter(Player::isActive)
            .toList();
        List<MatchParticipant> matchParticipants =
            matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(groupId);

        Map<Long, List<MatchParticipant>> historyByPlayerId = new HashMap<>();
        for (MatchParticipant matchParticipant : matchParticipants) {
            Long playerId = matchParticipant.getPlayer().getId();
            historyByPlayerId.computeIfAbsent(playerId, key -> new ArrayList<>()).add(matchParticipant);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<RankingCandidate> candidates = new ArrayList<>();
        for (Player player : players) {
            List<MatchParticipant> history =
                historyByPlayerId.getOrDefault(player.getId(), Collections.emptyList());
            RankingStats stats = calculateStats(history);
            int currentMmr = safeInt(player.getMmr());
            String currentTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);
            DormancyAdjustment dormancyAdjustment = applyDormancyAdjustment(
                currentTier,
                currentMmr,
                resolveLastPlayedAt(history),
                player.getCreatedAt(),
                now
            );

            candidates.add(new RankingCandidate(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                dormancyAdjustment.tier(),
                dormancyAdjustment.mmr(),
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

    private OffsetDateTime resolveLastPlayedAt(List<MatchParticipant> history) {
        for (MatchParticipant participant : history) {
            if (participant.getMatch() != null && participant.getMatch().getPlayedAt() != null) {
                return participant.getMatch().getPlayedAt();
            }
        }
        return null;
    }

    private DormancyAdjustment applyDormancyAdjustment(
        String currentTier,
        int currentMmr,
        OffsetDateTime lastPlayedAt,
        OffsetDateTime createdAt,
        OffsetDateTime now
    ) {
        if (!dormancyEnabled || dormancyDemoteSteps <= 0) {
            return new DormancyAdjustment(currentTier, currentMmr);
        }

        OffsetDateTime activityReference = lastPlayedAt != null ? lastPlayedAt : createdAt;
        if (activityReference == null) {
            return new DormancyAdjustment(currentTier, currentMmr);
        }

        long inactiveDays = ChronoUnit.DAYS.between(activityReference.toInstant(), now.toInstant());
        if (inactiveDays < dormancyInactiveDays) {
            return new DormancyAdjustment(currentTier, currentMmr);
        }

        String demotedTier = PlayerTierPolicy.demoteTier(currentTier, dormancyDemoteSteps);
        int demotedMmr = PlayerTierPolicy.resolveDormancyAdjustedMmr(
            currentTier,
            currentMmr,
            dormancyDemoteSteps
        );
        String effectiveTier = PlayerTierPolicy.resolveTierForSnapshot(demotedTier, demotedMmr);
        return new DormancyAdjustment(effectiveTier, demotedMmr);
    }

    private RankingStats calculateStats(List<MatchParticipant> history) {
        int wins = 0;
        int losses = 0;
        int mmrDelta = 0;

        Result streakResult = null;
        int streakCount = 0;
        boolean streakOpen = true;
        List<String> last10 = new ArrayList<>();

        for (MatchParticipant matchParticipant : history) {
            mmrDelta += safeInt(matchParticipant.getMmrDelta());

            Result result = resolveResult(matchParticipant);
            if (result == Result.UNKNOWN) {
                continue;
            }

            if (result == Result.WIN) {
                wins++;
            } else {
                losses++;
            }

            if (last10.size() < 10) {
                last10.add(result.symbol());
            }

            if (streakOpen) {
                if (streakResult == null) {
                    streakResult = result;
                    streakCount = 1;
                } else if (streakResult == result) {
                    streakCount++;
                } else {
                    streakOpen = false;
                }
            }
        }

        int games = wins + losses;
        double winRate = games == 0 ? 0.0 : round2((wins * 100.0) / games);
        String streak =
            streakResult == null ? "N0" : streakResult.symbol() + streakCount;
        String last10Value = String.join("", last10);

        return new RankingStats(wins, losses, games, winRate, streak, last10Value, mmrDelta);
    }

    private Result resolveResult(MatchParticipant matchParticipant) {
        if (matchParticipant.getMatch() == null
            || matchParticipant.getMatch().getWinningTeam() == null
            || matchParticipant.getTeam() == null
            || matchParticipant.getTeam().isBlank()) {
            return Result.UNKNOWN;
        }

        return matchParticipant.getMatch().getWinningTeam()
            .equalsIgnoreCase(matchParticipant.getTeam())
            ? Result.WIN
            : Result.LOSS;
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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

    private record DormancyAdjustment(
        String tier,
        int mmr
    ) {
    }

    private enum Result {
        WIN("W"),
        LOSS("L"),
        UNKNOWN("N");

        private final String symbol;

        Result(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }
}
