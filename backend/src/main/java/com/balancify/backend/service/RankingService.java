package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RankingService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    public RankingService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    public List<RankingItemResponse> getGroupRanking(Long groupId) {
        List<Player> players = playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId);
        List<MatchParticipant> matchParticipants =
            matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(groupId);

        Map<Long, List<MatchParticipant>> historyByPlayerId = new HashMap<>();
        for (MatchParticipant matchParticipant : matchParticipants) {
            Long playerId = matchParticipant.getPlayer().getId();
            historyByPlayerId.computeIfAbsent(playerId, key -> new ArrayList<>()).add(matchParticipant);
        }

        List<RankingItemResponse> ranking = new ArrayList<>();
        int rank = 1;
        for (Player player : players) {
            List<MatchParticipant> history =
                historyByPlayerId.getOrDefault(player.getId(), Collections.emptyList());
            RankingStats stats = calculateStats(history);

            ranking.add(new RankingItemResponse(
                rank++,
                player.getNickname(),
                normalizeRace(player.getRace()),
                safeInt(player.getMmr()),
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
        if (race == null || race.isBlank()) {
            return "P";
        }

        String normalized = race.trim().toUpperCase();
        return switch (normalized) {
            case "P", "T", "Z", "PT", "PZ", "TZ", "R" -> normalized;
            default -> "P";
        };
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
