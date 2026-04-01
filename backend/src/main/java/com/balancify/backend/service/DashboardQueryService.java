package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.DashboardKpiSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalancePreviewResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalanceTeamPlayerResponse;
import com.balancify.backend.api.group.dto.DashboardTopRankingPreviewItemResponse;
import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DashboardQueryService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchRepository matchRepository;

    public DashboardQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        MatchRepository matchRepository
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchRepository = matchRepository;
    }

    public GroupDashboardResponse getGroupDashboard(Long groupId) {
        List<Player> players = new ArrayList<>(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId));
        players.sort(
            Comparator
                .comparingInt((Player player) -> safeInt(player.getMmr()))
                .reversed()
                .thenComparing(Player::getId, Comparator.nullsLast(Long::compareTo))
        );

        List<MatchParticipant> matchParticipants =
            matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(groupId);

        Map<Long, StatsAccumulator> statsByPlayerId = new HashMap<>();
        Set<Long> rankedMatchIds = new HashSet<>();
        for (MatchParticipant matchParticipant : matchParticipants) {
            if (matchParticipant.getPlayer() == null || matchParticipant.getPlayer().getId() == null) {
                continue;
            }

            Long playerId = matchParticipant.getPlayer().getId();
            StatsAccumulator stats =
                statsByPlayerId.computeIfAbsent(playerId, ignored -> new StatsAccumulator());

            Result result = resolveResult(matchParticipant);
            if (result == Result.WIN) {
                stats.wins++;
            } else if (result == Result.LOSS) {
                stats.losses++;
            }

            if (result != Result.UNKNOWN
                && matchParticipant.getMatch() != null
                && matchParticipant.getMatch().getId() != null) {
                rankedMatchIds.add(matchParticipant.getMatch().getId());
            }
        }

        int totalPlayers = players.size();
        int topMmr = players.isEmpty() ? 0 : safeInt(players.get(0).getMmr());
        double averageMmr = totalPlayers == 0
            ? 0.0
            : round2(players.stream().mapToInt(player -> safeInt(player.getMmr())).average().orElse(0.0));
        int totalGames = rankedMatchIds.size();

        DashboardKpiSummaryResponse kpiSummary = new DashboardKpiSummaryResponse(
            totalPlayers,
            topMmr,
            averageMmr,
            totalGames
        );

        List<DashboardTopRankingPreviewItemResponse> topRankingPreview = new ArrayList<>();
        int rank = 1;
        for (Player player : players.stream().limit(5).toList()) {
            StatsAccumulator stats = statsByPlayerId.getOrDefault(player.getId(), new StatsAccumulator());
            int games = stats.wins + stats.losses;
            double winRate = games == 0 ? 0.0 : round2((stats.wins * 100.0) / games);

            topRankingPreview.add(new DashboardTopRankingPreviewItemResponse(
                rank++,
                player.getNickname(),
                normalizeRace(player.getRace()),
                safeInt(player.getMmr()),
                winRate
            ));
        }

        DashboardRecentBalancePreviewResponse recentBalancePreview =
            buildRecentBalancePreview(groupId).orElse(null);

        return new GroupDashboardResponse(
            kpiSummary,
            topRankingPreview,
            recentBalancePreview
        );
    }

    private Optional<DashboardRecentBalancePreviewResponse> buildRecentBalancePreview(Long groupId) {
        Optional<Match> latestMatch =
            matchRepository.findTopByGroup_IdOrderByPlayedAtDescIdDesc(groupId);
        if (latestMatch.isEmpty()) {
            return Optional.empty();
        }

        Match match = latestMatch.get();
        if (match.getId() == null) {
            return Optional.empty();
        }

        List<MatchParticipant> participants =
            matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId());
        if (participants.isEmpty()) {
            return Optional.empty();
        }

        List<DashboardRecentBalanceTeamPlayerResponse> homeTeam = new ArrayList<>();
        List<DashboardRecentBalanceTeamPlayerResponse> awayTeam = new ArrayList<>();
        int homeMmr = 0;
        int awayMmr = 0;

        for (MatchParticipant participant : participants) {
            if (participant.getPlayer() == null) {
                continue;
            }

            String team = participant.getTeam() == null ? "" : participant.getTeam().trim().toUpperCase();
            int mmr = participant.getMmrBefore() != null
                ? participant.getMmrBefore()
                : safeInt(participant.getPlayer().getMmr());
            DashboardRecentBalanceTeamPlayerResponse playerResponse =
                new DashboardRecentBalanceTeamPlayerResponse(participant.getPlayer().getNickname(), mmr);

            if ("HOME".equals(team)) {
                homeTeam.add(playerResponse);
                homeMmr += mmr;
            } else if ("AWAY".equals(team)) {
                awayTeam.add(playerResponse);
                awayMmr += mmr;
            }
        }

        if (homeTeam.isEmpty() && awayTeam.isEmpty()) {
            return Optional.empty();
        }

        DashboardRecentBalancePreviewResponse response = new DashboardRecentBalancePreviewResponse(
            match.getId(),
            homeTeam,
            awayTeam,
            homeMmr,
            awayMmr,
            Math.abs(homeMmr - awayMmr),
            match.getPlayedAt()
        );

        return Optional.of(response);
    }

    private Result resolveResult(MatchParticipant matchParticipant) {
        if (matchParticipant.getMatch() == null
            || matchParticipant.getMatch().getWinningTeam() == null
            || matchParticipant.getTeam() == null
            || matchParticipant.getTeam().isBlank()) {
            return Result.UNKNOWN;
        }

        return matchParticipant.getMatch().getWinningTeam().equalsIgnoreCase(matchParticipant.getTeam())
            ? Result.WIN
            : Result.LOSS;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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

    private enum Result {
        WIN,
        LOSS,
        UNKNOWN
    }

    private static class StatsAccumulator {
        private int wins;
        private int losses;
    }
}
