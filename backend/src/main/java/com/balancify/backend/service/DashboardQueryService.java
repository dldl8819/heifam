package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.DashboardKpiSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardMyGameTypeStatResponse;
import com.balancify.backend.api.group.dto.DashboardMyGameTypeSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardMyRaceStatResponse;
import com.balancify.backend.api.group.dto.DashboardMyRaceSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardMyTeammateStatResponse;
import com.balancify.backend.api.group.dto.DashboardMyTeammateSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalancePreviewResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalanceTeamPlayerResponse;
import com.balancify.backend.api.group.dto.DashboardTopRankingPreviewItemResponse;
import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardQueryService {

    private static final List<String> DASHBOARD_RACE_ORDER = List.of("P", "T", "Z", "PT", "PZ", "TZ", "PTZ");
    private static final List<String> GAME_TYPE_RACE_ORDER = List.of("P", "T", "Z", "PTZ");
    private static final int TEAMMATE_MIN_GAMES = 10;
    private static final int TEAMMATE_PREVIEW_LIMIT = 3;

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchRepository matchRepository;
    private final int currentKFactor;

    public DashboardQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        MatchRepository matchRepository,
        @Value("${balancify.elo.k-factor:36}") int currentKFactor
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchRepository = matchRepository;
        this.currentKFactor = currentKFactor;
    }

    public GroupDashboardResponse getGroupDashboard(Long groupId) {
        return getGroupDashboard(groupId, null);
    }

    public GroupDashboardResponse getGroupDashboard(Long groupId, String requesterNickname) {
        List<Player> players = new ArrayList<>(
            playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
                .stream()
                .filter(Player::isActive)
                .toList()
        );
        players.sort(
            Comparator
                .comparingInt((Player player) -> safeInt(player.getMmr()))
                .reversed()
                .thenComparing(Player::getId, Comparator.nullsLast(Long::compareTo))
        );

        int totalPlayers = players.size();
        int topMmr = players.isEmpty() ? 0 : safeInt(players.get(0).getMmr());
        double averageMmr = totalPlayers == 0
            ? 0.0
            : round2(players.stream().mapToInt(player -> safeInt(player.getMmr())).average().orElse(0.0));
        int totalGames = safeLongToInt(matchRepository.countByGroup_IdAndWinningTeamIsNotNull(groupId));

        DashboardKpiSummaryResponse kpiSummary = new DashboardKpiSummaryResponse(
            totalPlayers,
            topMmr,
            averageMmr,
            totalGames
        );

        Player targetPlayer = findRequesterPlayer(players, requesterNickname);
        if (targetPlayer == null) {
            return new GroupDashboardResponse(
                currentKFactor,
                kpiSummary,
                List.of(),
                null,
                emptyMyRaceSummary(requestedNicknameOrNull(requesterNickname)),
                emptyMyGameTypeSummary(requestedNicknameOrNull(requesterNickname)),
                emptyMyTeammateSummary(requestedNicknameOrNull(requesterNickname))
            );
        }

        List<MatchParticipant> matchParticipants =
            matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(groupId);
        Map<Long, Map<String, String>> teamCompositionByMatchTeam =
            buildTeamCompositionByMatchTeam(matchParticipants);

        Map<Long, List<MatchParticipant>> historyByPlayerId = new HashMap<>();
        Map<Long, List<MatchParticipant>> participantsByMatchId = new HashMap<>();
        for (MatchParticipant matchParticipant : matchParticipants) {
            if (matchParticipant.getMatch() != null && matchParticipant.getMatch().getId() != null) {
                participantsByMatchId
                    .computeIfAbsent(matchParticipant.getMatch().getId(), ignored -> new ArrayList<>())
                    .add(matchParticipant);
            }

            if (matchParticipant.getPlayer() == null || matchParticipant.getPlayer().getId() == null) {
                continue;
            }

            Long playerId = matchParticipant.getPlayer().getId();
            historyByPlayerId.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(matchParticipant);
        }

        DashboardMyRaceSummaryResponse myRaceSummary =
            buildMyRaceSummary(historyByPlayerId, targetPlayer, requesterNickname);
        DashboardMyGameTypeSummaryResponse myGameTypeSummary =
            buildMyGameTypeSummary(
                historyByPlayerId,
                teamCompositionByMatchTeam,
                targetPlayer,
                requesterNickname
            );
        DashboardMyTeammateSummaryResponse myTeammateSummary =
            buildMyTeammateSummary(
                historyByPlayerId,
                participantsByMatchId,
                targetPlayer,
                requesterNickname
            );

        return new GroupDashboardResponse(
            currentKFactor,
            kpiSummary,
            List.of(),
            null,
            myRaceSummary,
            myGameTypeSummary,
            myTeammateSummary
        );
    }

    private Optional<DashboardRecentBalancePreviewResponse> buildRecentBalancePreview(Long groupId) {
        Optional<Match> latestMatch =
            matchRepository.findTopByGroup_IdAndStatusOrderByPlayedAtDescIdDesc(groupId, MatchStatus.COMPLETED);
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

    private DashboardMyRaceSummaryResponse buildMyRaceSummary(
        Map<Long, List<MatchParticipant>> historyByPlayerId,
        Player targetPlayer,
        String requesterNickname
    ) {
        if (targetPlayer == null || targetPlayer.getId() == null) {
            return emptyMyRaceSummary(requestedNicknameOrNull(requesterNickname));
        }

        List<MatchParticipant> history = historyByPlayerId.getOrDefault(targetPlayer.getId(), List.of());
        Map<String, RaceStatsAccumulator> raceStatsByRace = new LinkedHashMap<>();
        int totalWins = 0;
        int totalLosses = 0;
        for (MatchParticipant participant : history) {
            Result result = resolveResult(participant);
            if (result == Result.UNKNOWN) {
                continue;
            }

            String race = resolveParticipantRace(participant);
            RaceStatsAccumulator raceStats =
                raceStatsByRace.computeIfAbsent(race, ignored -> new RaceStatsAccumulator());
            if (result == Result.WIN) {
                raceStats.wins++;
                totalWins++;
            } else {
                raceStats.losses++;
                totalLosses++;
            }
        }

        List<DashboardMyRaceStatResponse> byRace = raceStatsByRace
            .entrySet()
            .stream()
            .map(entry -> {
                int wins = entry.getValue().wins;
                int losses = entry.getValue().losses;
                int games = wins + losses;
                double winRate = games == 0 ? 0.0 : round2((wins * 100.0) / games);
                return new DashboardMyRaceStatResponse(entry.getKey(), wins, losses, games, winRate);
            })
            .sorted((left, right) -> {
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
            })
            .toList();

        int totalGames = totalWins + totalLosses;
        double totalWinRate = totalGames == 0 ? 0.0 : round2((totalWins * 100.0) / totalGames);

        return new DashboardMyRaceSummaryResponse(
            true,
            targetPlayer.getNickname(),
            totalWins,
            totalLosses,
            totalGames,
            totalWinRate,
            byRace
        );
    }

    private DashboardMyGameTypeSummaryResponse buildMyGameTypeSummary(
        Map<Long, List<MatchParticipant>> historyByPlayerId,
        Map<Long, Map<String, String>> teamCompositionByMatchTeam,
        Player targetPlayer,
        String requesterNickname
    ) {
        if (targetPlayer == null || targetPlayer.getId() == null) {
            return emptyMyGameTypeSummary(requestedNicknameOrNull(requesterNickname));
        }

        List<MatchParticipant> history = historyByPlayerId.getOrDefault(targetPlayer.getId(), List.of());
        Map<String, RaceStatsAccumulator> gameTypeStats = new LinkedHashMap<>();
        int totalWins = 0;
        int totalLosses = 0;
        for (MatchParticipant participant : history) {
            Result result = resolveResult(participant);
            if (result == Result.UNKNOWN) {
                continue;
            }

            String gameType = resolveTeamCompositionType(participant, teamCompositionByMatchTeam);
            RaceStatsAccumulator stats =
                gameTypeStats.computeIfAbsent(gameType, ignored -> new RaceStatsAccumulator());
            if (result == Result.WIN) {
                stats.wins++;
                totalWins++;
            } else {
                stats.losses++;
                totalLosses++;
            }
        }

        List<DashboardMyGameTypeStatResponse> byGameType = gameTypeStats
            .entrySet()
            .stream()
            .map(entry -> {
                int wins = entry.getValue().wins;
                int losses = entry.getValue().losses;
                int games = wins + losses;
                double winRate = games == 0 ? 0.0 : round2((wins * 100.0) / games);
                return new DashboardMyGameTypeStatResponse(entry.getKey(), wins, losses, games, winRate);
            })
            .sorted((left, right) -> {
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
            })
            .toList();

        int totalGames = totalWins + totalLosses;
        double totalWinRate = totalGames == 0 ? 0.0 : round2((totalWins * 100.0) / totalGames);

        return new DashboardMyGameTypeSummaryResponse(
            true,
            targetPlayer.getNickname(),
            totalWins,
            totalLosses,
            totalGames,
            totalWinRate,
            byGameType
        );
    }

    private DashboardMyTeammateSummaryResponse buildMyTeammateSummary(
        Map<Long, List<MatchParticipant>> historyByPlayerId,
        Map<Long, List<MatchParticipant>> participantsByMatchId,
        Player targetPlayer,
        String requesterNickname
    ) {
        if (targetPlayer == null || targetPlayer.getId() == null) {
            return emptyMyTeammateSummary(requestedNicknameOrNull(requesterNickname));
        }

        List<MatchParticipant> history = historyByPlayerId.getOrDefault(targetPlayer.getId(), List.of());
        Map<Long, TeammateStatsAccumulator> statsByTeammateId = new HashMap<>();
        for (MatchParticipant targetParticipant : history) {
            if (targetParticipant.getMatch() == null || targetParticipant.getMatch().getId() == null) {
                continue;
            }

            Result result = resolveResult(targetParticipant);
            if (result == Result.UNKNOWN) {
                continue;
            }

            String targetTeam = normalizeTeam(targetParticipant.getTeam());
            if (targetTeam.isEmpty()) {
                continue;
            }

            List<MatchParticipant> teammates =
                participantsByMatchId.getOrDefault(targetParticipant.getMatch().getId(), List.of());
            for (MatchParticipant teammateParticipant : teammates) {
                if (teammateParticipant == null
                    || teammateParticipant.getPlayer() == null
                    || teammateParticipant.getPlayer().getId() == null
                    || teammateParticipant.getPlayer().getId().equals(targetPlayer.getId())
                    || !targetTeam.equals(normalizeTeam(teammateParticipant.getTeam()))) {
                    continue;
                }

                Player teammate = teammateParticipant.getPlayer();
                TeammateStatsAccumulator stats =
                    statsByTeammateId.computeIfAbsent(teammate.getId(), ignored -> new TeammateStatsAccumulator());
                stats.nickname = teammate.getNickname();
                if (result == Result.WIN) {
                    stats.wins++;
                } else {
                    stats.losses++;
                }
                stats.recentResults.add(result);
            }
        }

        List<DashboardMyTeammateStatResponse> eligibleStats = statsByTeammateId
            .values()
            .stream()
            .map(this::toTeammateStatResponse)
            .filter(stat -> stat.games() >= TEAMMATE_MIN_GAMES)
            .toList();

        List<DashboardMyTeammateStatResponse> bestDuos = eligibleStats
            .stream()
            .sorted(this::compareBestDuo)
            .limit(TEAMMATE_PREVIEW_LIMIT)
            .toList();

        List<DashboardMyTeammateStatResponse> frequentTeammates = eligibleStats
            .stream()
            .sorted(this::compareFrequentTeammate)
            .limit(TEAMMATE_PREVIEW_LIMIT)
            .toList();

        List<DashboardMyTeammateStatResponse> streakPartners = eligibleStats
            .stream()
            .filter(stat -> stat.currentWinStreak() > 0)
            .sorted(this::compareStreakPartner)
            .limit(TEAMMATE_PREVIEW_LIMIT)
            .toList();

        return new DashboardMyTeammateSummaryResponse(
            true,
            targetPlayer.getNickname(),
            TEAMMATE_MIN_GAMES,
            bestDuos,
            frequentTeammates,
            streakPartners
        );
    }

    private Map<Long, Map<String, String>> buildTeamCompositionByMatchTeam(List<MatchParticipant> matchParticipants) {
        Map<Long, Map<String, List<String>>> racesByMatchTeam = new HashMap<>();
        for (MatchParticipant participant : matchParticipants) {
            if (participant == null
                || participant.getMatch() == null
                || participant.getMatch().getId() == null) {
                continue;
            }

            String team = normalizeTeam(participant.getTeam());
            if (team.isEmpty()) {
                continue;
            }

            Long matchId = participant.getMatch().getId();
            String raceToken = normalizeGameTypeRace(resolveParticipantRace(participant));
            racesByMatchTeam
                .computeIfAbsent(matchId, ignored -> new HashMap<>())
                .computeIfAbsent(team, ignored -> new ArrayList<>())
                .add(raceToken);
        }

        Map<Long, Map<String, String>> teamCompositionByMatchTeam = new HashMap<>();
        for (Map.Entry<Long, Map<String, List<String>>> matchEntry : racesByMatchTeam.entrySet()) {
            Map<String, String> teamComposition = new HashMap<>();
            for (Map.Entry<String, List<String>> teamEntry : matchEntry.getValue().entrySet()) {
                String composition = composeGameType(teamEntry.getValue());
                if (!composition.isBlank()) {
                    teamComposition.put(teamEntry.getKey(), composition);
                }
            }
            if (!teamComposition.isEmpty()) {
                teamCompositionByMatchTeam.put(matchEntry.getKey(), teamComposition);
            }
        }
        return teamCompositionByMatchTeam;
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

    private int safeLongToInt(Long value) {
        if (value == null) {
            return 0;
        }
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private String resolveParticipantRace(MatchParticipant participant) {
        if (participant == null) {
            return "P";
        }
        String assignedRace = participant.getAssignedRace();
        if (assignedRace != null && !assignedRace.isBlank()) {
            return PlayerRacePolicy.normalizeAssignedRace(assignedRace);
        }
        String participantRace = participant.getRace();
        if (participantRace != null && !participantRace.isBlank()) {
            return normalizeRace(participantRace);
        }
        if (participant.getPlayer() != null) {
            return normalizeRace(participant.getPlayer().getRace());
        }
        return "P";
    }

    private String resolveTeamCompositionType(
        MatchParticipant participant,
        Map<Long, Map<String, String>> teamCompositionByMatchTeam
    ) {
        if (participant != null
            && participant.getMatch() != null
            && participant.getMatch().getId() != null) {
            Long matchId = participant.getMatch().getId();
            String team = normalizeTeam(participant.getTeam());
            if (!team.isEmpty()) {
                Map<String, String> teamComposition = teamCompositionByMatchTeam.get(matchId);
                if (teamComposition != null) {
                    String composed = teamComposition.get(team);
                    if (composed != null && !composed.isBlank()) {
                        return composed;
                    }
                }
            }
        }
        return normalizeGameTypeRace(resolveParticipantRace(participant));
    }

    private String normalizeTeam(String team) {
        String normalized = safeTrim(team).toUpperCase();
        if ("HOME".equals(normalized) || "AWAY".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private String normalizeGameTypeRace(String race) {
        String normalized = normalizeRace(race);
        return switch (normalized) {
            case "P", "T", "Z" -> normalized;
            default -> "PTZ";
        };
    }

    private String composeGameType(List<String> races) {
        if (races == null || races.isEmpty()) {
            return "";
        }

        List<String> normalizedRaces = races
            .stream()
            .map(this::normalizeGameTypeRace)
            .sorted(Comparator.comparingInt(this::gameTypeRaceOrder))
            .toList();
        return String.join("", normalizedRaces);
    }

    private DashboardMyRaceSummaryResponse emptyMyRaceSummary(String nickname) {
        return new DashboardMyRaceSummaryResponse(
            false,
            nickname,
            0,
            0,
            0,
            0.0,
            List.of()
        );
    }

    private DashboardMyGameTypeSummaryResponse emptyMyGameTypeSummary(String nickname) {
        return new DashboardMyGameTypeSummaryResponse(
            false,
            nickname,
            0,
            0,
            0,
            0.0,
            List.of()
        );
    }

    private DashboardMyTeammateSummaryResponse emptyMyTeammateSummary(String nickname) {
        return new DashboardMyTeammateSummaryResponse(
            false,
            nickname,
            TEAMMATE_MIN_GAMES,
            List.of(),
            List.of(),
            List.of()
        );
    }

    private DashboardMyTeammateStatResponse toTeammateStatResponse(TeammateStatsAccumulator stats) {
        int games = stats.wins + stats.losses;
        double winRate = games == 0 ? 0.0 : round2((stats.wins * 100.0) / games);
        int currentWinStreak = 0;
        for (Result result : stats.recentResults) {
            if (result != Result.WIN) {
                break;
            }
            currentWinStreak++;
        }

        return new DashboardMyTeammateStatResponse(
            stats.nickname,
            stats.wins,
            stats.losses,
            games,
            winRate,
            currentWinStreak
        );
    }

    private int compareBestDuo(
        DashboardMyTeammateStatResponse left,
        DashboardMyTeammateStatResponse right
    ) {
        int winRateComparison = Double.compare(right.winRate(), left.winRate());
        if (winRateComparison != 0) {
            return winRateComparison;
        }
        if (right.wins() != left.wins()) {
            return Integer.compare(right.wins(), left.wins());
        }
        if (right.games() != left.games()) {
            return Integer.compare(right.games(), left.games());
        }
        return compareNullableNickname(left.nickname(), right.nickname());
    }

    private int compareFrequentTeammate(
        DashboardMyTeammateStatResponse left,
        DashboardMyTeammateStatResponse right
    ) {
        if (right.games() != left.games()) {
            return Integer.compare(right.games(), left.games());
        }
        if (right.wins() != left.wins()) {
            return Integer.compare(right.wins(), left.wins());
        }
        int winRateComparison = Double.compare(right.winRate(), left.winRate());
        if (winRateComparison != 0) {
            return winRateComparison;
        }
        return compareNullableNickname(left.nickname(), right.nickname());
    }

    private int compareStreakPartner(
        DashboardMyTeammateStatResponse left,
        DashboardMyTeammateStatResponse right
    ) {
        if (right.currentWinStreak() != left.currentWinStreak()) {
            return Integer.compare(right.currentWinStreak(), left.currentWinStreak());
        }
        if (right.games() != left.games()) {
            return Integer.compare(right.games(), left.games());
        }
        int winRateComparison = Double.compare(right.winRate(), left.winRate());
        if (winRateComparison != 0) {
            return winRateComparison;
        }
        return compareNullableNickname(left.nickname(), right.nickname());
    }

    private int compareNullableNickname(String left, String right) {
        return safeTrim(left).compareToIgnoreCase(safeTrim(right));
    }

    private Player findRequesterPlayer(List<Player> players, String requesterNickname) {
        String normalizedNickname = safeTrim(requesterNickname);
        if (normalizedNickname.isEmpty()) {
            return null;
        }
        return players
            .stream()
            .filter(player -> player.getNickname() != null)
            .filter(player -> player.getNickname().equalsIgnoreCase(normalizedNickname))
            .findFirst()
            .orElse(null);
    }

    private String requestedNicknameOrNull(String requesterNickname) {
        String normalizedNickname = safeTrim(requesterNickname);
        return normalizedNickname.isEmpty() ? null : normalizedNickname;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private int raceOrder(String race) {
        int index = DASHBOARD_RACE_ORDER.indexOf(race);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private int gameTypeRaceOrder(String race) {
        int index = GAME_TYPE_RACE_ORDER.indexOf(race);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private int compareGameType(String left, String right) {
        int minLength = Math.min(left.length(), right.length());
        for (int i = 0; i < minLength; i++) {
            int leftOrder = gameTypeRaceOrder(String.valueOf(left.charAt(i)));
            int rightOrder = gameTypeRaceOrder(String.valueOf(right.charAt(i)));
            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }
        }
        if (left.length() != right.length()) {
            return Integer.compare(left.length(), right.length());
        }
        return 0;
    }

    private enum Result {
        WIN,
        LOSS,
        UNKNOWN
    }

    private static class RaceStatsAccumulator {
        private int wins;
        private int losses;
    }

    private static class TeammateStatsAccumulator {
        private String nickname;
        private int wins;
        private int losses;
        private final List<Result> recentResults = new ArrayList<>();
    }
}
