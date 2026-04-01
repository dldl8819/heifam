package com.balancify.backend.service;

import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMode;
import com.balancify.backend.api.match.dto.MultiBalancePenaltySummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRaceSummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRequest;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceWaitingPlayerResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MultiMatchBalancingService {

    private static final int TEAM_SIZE_TWO = 2;
    private static final int TEAM_SIZE_THREE = 3;
    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final String MATCH_TYPE_2V2 = "2v2";
    private static final String MATCH_TYPE_3V3 = "3v3";

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final TeamBalancingService teamBalancingService;
    private final int recentMatchLookbackCount;
    private final double mmrDiffWeight;
    private final double interMatchBalanceWeight;
    private final double repeatTeammatePenaltyWeight;
    private final double repeatMatchupPenaltyWeight;
    private final double racePenaltyWeight;

    public MultiMatchBalancingService(
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        TeamBalancingService teamBalancingService,
        @Value("${balancify.multi-balance.recent-lookback-count:10}") int recentMatchLookbackCount,
        @Value("${balancify.multi-balance.internal-mmr-diff-weight:1000}") double mmrDiffWeight,
        @Value("${balancify.multi-balance.inter-match-balance-weight:150}") double interMatchBalanceWeight,
        @Value("${balancify.multi-balance.repeat-teammate-penalty-weight:2.0}") double repeatTeammatePenaltyWeight,
        @Value("${balancify.multi-balance.repeat-matchup-penalty-weight:1.5}") double repeatMatchupPenaltyWeight,
        @Value("${balancify.multi-balance.race-penalty-weight:1.0}") double racePenaltyWeight
    ) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.teamBalancingService = teamBalancingService;
        this.recentMatchLookbackCount = Math.max(1, recentMatchLookbackCount);
        this.mmrDiffWeight = mmrDiffWeight;
        this.interMatchBalanceWeight = interMatchBalanceWeight;
        this.repeatTeammatePenaltyWeight = repeatTeammatePenaltyWeight;
        this.repeatMatchupPenaltyWeight = repeatMatchupPenaltyWeight;
        this.racePenaltyWeight = racePenaltyWeight;
    }

    public MultiBalanceResponse balance(MultiBalanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        Long groupId = request.groupId();
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException("groupId must be a positive number");
        }

        List<Long> playerIds = request.playerIds();
        if (playerIds == null || playerIds.isEmpty()) {
            throw new IllegalArgumentException("playerIds is required");
        }
        if (playerIds.size() < 4) {
            throw new IllegalArgumentException("At least 4 players are required");
        }

        Set<Long> uniqueIds = new HashSet<>(playerIds);
        if (uniqueIds.size() != playerIds.size()) {
            throw new IllegalArgumentException("playerIds must not contain duplicates");
        }

        List<PlayerSnapshot> orderedPlayers = loadPlayers(groupId, playerIds);
        MultiBalanceMode balanceMode = MultiBalanceMode.fromNullable(request.balanceMode());
        WeightProfile weightProfile = resolveWeightProfile(balanceMode);

        MatchAllocationPlan allocationPlan = buildAllocationPlan(orderedPlayers.size());
        int assignedPlayersCount = allocationPlan.assignedPlayers();
        int waitingPlayersCount = allocationPlan.waitingPlayers();

        List<PlayerSnapshot> sortedPlayers = orderedPlayers.stream()
            .sorted(Comparator.comparingInt(PlayerSnapshot::mmr).reversed().thenComparingLong(PlayerSnapshot::id))
            .toList();

        List<PlayerSnapshot> assignedPlayers = new ArrayList<>(
            sortedPlayers.subList(0, Math.max(0, sortedPlayers.size() - waitingPlayersCount))
        );
        List<PlayerSnapshot> waitingPlayers = new ArrayList<>(
            sortedPlayers.subList(Math.max(0, sortedPlayers.size() - waitingPlayersCount), sortedPlayers.size())
        );

        RecentHistory history = loadRecentHistory(groupId);
        List<MatchGroup> distributedGroups = distributePlayersToGroups(assignedPlayers, allocationPlan.teamSizes());
        List<MatchEvaluation> evaluatedMatches = evaluateGroups(distributedGroups, history, weightProfile);

        List<MultiBalanceMatchResponse> matches = evaluatedMatches.stream()
            .map(MatchEvaluation::response)
            .toList();
        List<MultiBalanceWaitingPlayerResponse> waitingResponses = waitingPlayers.stream()
            .map(player -> new MultiBalanceWaitingPlayerResponse(player.id(), player.nickname()))
            .toList();

        return new MultiBalanceResponse(
            balanceMode.name(),
            orderedPlayers.size(),
            assignedPlayersCount,
            waitingResponses,
            matches.size(),
            matches
        );
    }

    private List<PlayerSnapshot> loadPlayers(Long groupId, List<Long> playerIds) {
        List<Player> loaded = playerRepository.findByGroup_IdAndIdIn(groupId, playerIds);
        if (loaded.size() != playerIds.size()) {
            Set<Long> foundIds = new HashSet<>();
            loaded.forEach(player -> foundIds.add(player.getId()));
            List<Long> missing = playerIds.stream().filter(id -> !foundIds.contains(id)).distinct().toList();
            throw new IllegalArgumentException("Players not found in group: " + missing);
        }

        Map<Long, Player> byId = new HashMap<>();
        for (Player player : loaded) {
            byId.put(player.getId(), player);
        }

        List<PlayerSnapshot> ordered = new ArrayList<>();
        for (Long playerId : playerIds) {
            Player player = byId.get(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Players not found in group: [" + playerId + "]");
            }
            ordered.add(new PlayerSnapshot(
                player.getId(),
                player.getNickname(),
                safeMmr(player.getMmr()),
                normalizeRace(player.getRace())
            ));
        }
        return ordered;
    }

    private MatchAllocationPlan buildAllocationPlan(int totalPlayers) {
        int match3Count = totalPlayers / 6;
        int remaining = totalPlayers - (match3Count * 6);

        if (remaining == 2 && match3Count > 0 && totalPlayers < 18) {
            match3Count -= 1;
            remaining += 6;
        }

        int match2Count = remaining / 4;
        int waitingCount = remaining - (match2Count * 4);

        List<Integer> teamSizes = new ArrayList<>();
        for (int index = 0; index < match3Count; index++) {
            teamSizes.add(TEAM_SIZE_THREE);
        }
        for (int index = 0; index < match2Count; index++) {
            teamSizes.add(TEAM_SIZE_TWO);
        }

        int assignedCount = (match3Count * 6) + (match2Count * 4);
        return new MatchAllocationPlan(teamSizes, assignedCount, waitingCount);
    }

    private List<MatchGroup> distributePlayersToGroups(List<PlayerSnapshot> sortedPlayers, List<Integer> teamSizes) {
        List<MatchGroup> groups = new ArrayList<>();
        for (int index = 0; index < teamSizes.size(); index++) {
            groups.add(new MatchGroup(index + 1, teamSizes.get(index)));
        }
        if (groups.isEmpty()) {
            return groups;
        }

        for (PlayerSnapshot player : sortedPlayers) {
            MatchGroup target = selectBestGroupForPlayer(groups, player);
            target.players().add(player);
            target.totalMmr = target.totalMmr + player.mmr();
        }
        return groups;
    }

    private MatchGroup selectBestGroupForPlayer(List<MatchGroup> groups, PlayerSnapshot player) {
        MatchGroup bestGroup = null;
        double bestScore = Double.MAX_VALUE;

        for (MatchGroup group : groups) {
            if (group.players().size() >= group.capacity()) {
                continue;
            }

            long sameRaceCount = group.players().stream()
                .filter(existing -> Objects.equals(existing.race(), player.race()))
                .count();
            double raceConcentrationPenalty = sameRaceCount * 300.0;
            double fillPenalty = group.players().size() * 50.0;
            double score = group.totalMmr + raceConcentrationPenalty + fillPenalty;

            if (bestGroup == null || score < bestScore || (score == bestScore && group.index() < bestGroup.index())) {
                bestGroup = group;
                bestScore = score;
            }
        }

        if (bestGroup == null) {
            throw new IllegalStateException("Unable to assign players to match groups");
        }
        return bestGroup;
    }

    private List<MatchEvaluation> evaluateGroups(
        List<MatchGroup> groups,
        RecentHistory history,
        WeightProfile weightProfile
    ) {
        List<MatchEvaluation> evaluations = new ArrayList<>();
        for (MatchGroup group : groups) {
            int teamSize = group.capacity() / 2;
            List<BalancePlayerDto> players = group.players().stream()
                .map(player -> new BalancePlayerDto(player.id(), player.nickname(), player.mmr()))
                .toList();

            MatchEvaluation evaluation = selectBestSplit(
                group.index(),
                teamSize,
                players,
                indexById(group.players()),
                history,
                weightProfile,
                evaluations
            );
            evaluations.add(evaluation);
        }
        return evaluations;
    }

    private MatchEvaluation selectBestSplit(
        int matchNumber,
        int teamSize,
        List<BalancePlayerDto> players,
        Map<Long, PlayerSnapshot> byId,
        RecentHistory history,
        WeightProfile weights,
        List<MatchEvaluation> selectedSoFar
    ) {
        List<TeamBalancingService.BalanceCandidate> candidates = teamBalancingService.generateCandidates(players, teamSize);
        CandidateScore best = null;

        for (TeamBalancingService.BalanceCandidate candidate : candidates) {
            int racePenalty = calculateRacePenalty(candidate, byId);
            int repeatTeammatePenalty = calculateRepeatTeammatePenalty(candidate, history.teammatePairCounts());
            int repeatMatchupPenalty = calculateRepeatMatchupPenalty(
                candidate,
                history.opponentPairCounts(),
                history.teamSignatureCounts(),
                history.matchupSignatureCounts()
            );
            int interMatchPenalty = calculateInterMatchPenalty(candidate, selectedSoFar);

            double score = (candidate.mmrDiff() * weights.mmrDiffWeight())
                + (interMatchPenalty * weights.interMatchWeight())
                + (racePenalty * weights.racePenaltyWeight())
                + (repeatTeammatePenalty * weights.repeatTeammateWeight())
                + (repeatMatchupPenalty * weights.repeatMatchupWeight());

            CandidateScore current = new CandidateScore(
                candidate,
                score,
                racePenalty,
                repeatTeammatePenalty,
                repeatMatchupPenalty,
                interMatchPenalty
            );
            if (best == null || current.isBetterThan(best)) {
                best = current;
            }
        }

        if (best == null) {
            throw new IllegalStateException("Unable to generate multi-match split");
        }

        TeamBalancingService.BalanceCandidate candidate = best.candidate();
        BalanceResponse balanced = teamBalancingService.toResponse(candidate);

        String homeRaceSummary = buildRaceSummary(candidate.homeTeam(), byId);
        String awayRaceSummary = buildRaceSummary(candidate.awayTeam(), byId);

        MultiBalanceMatchResponse response = new MultiBalanceMatchResponse(
            matchNumber,
            teamSize == TEAM_SIZE_THREE ? MATCH_TYPE_3V3 : MATCH_TYPE_2V2,
            teamSize,
            balanced.homeTeam(),
            balanced.awayTeam(),
            balanced.homeMmr(),
            balanced.awayMmr(),
            balanced.mmrDiff(),
            balanced.expectedHomeWinRate(),
            new MultiBalanceRaceSummaryResponse(homeRaceSummary, awayRaceSummary),
            new MultiBalancePenaltySummaryResponse(
                best.repeatTeammatePenalty(),
                best.repeatMatchupPenalty(),
                best.racePenalty()
            )
        );

        double groupAverage = (balanced.homeMmr() + balanced.awayMmr()) / (double) (teamSize * 2);
        return new MatchEvaluation(response, groupAverage);
    }

    private int calculateRacePenalty(
        TeamBalancingService.BalanceCandidate candidate,
        Map<Long, PlayerSnapshot> byId
    ) {
        int total = 0;
        total += countRaceConcentration(candidate.homeTeam(), byId);
        total += countRaceConcentration(candidate.awayTeam(), byId);

        Map<String, Integer> homeCounts = countRaces(candidate.homeTeam(), byId);
        Map<String, Integer> awayCounts = countRaces(candidate.awayTeam(), byId);
        Set<String> raceKeys = new HashSet<>();
        raceKeys.addAll(homeCounts.keySet());
        raceKeys.addAll(awayCounts.keySet());
        for (String race : raceKeys) {
            int homeCount = homeCounts.getOrDefault(race, 0);
            int awayCount = awayCounts.getOrDefault(race, 0);
            total += Math.abs(homeCount - awayCount);
        }
        return total;
    }

    private int countRaceConcentration(List<BalancePlayerDto> team, Map<Long, PlayerSnapshot> byId) {
        Map<String, Integer> counts = countRaces(team, byId);
        int penalty = 0;
        for (int count : counts.values()) {
            if (count > 1) {
                penalty += (count - 1) * (count - 1);
            }
        }
        return penalty;
    }

    private Map<String, Integer> countRaces(List<BalancePlayerDto> team, Map<Long, PlayerSnapshot> byId) {
        Map<String, Integer> counts = new HashMap<>();
        for (BalancePlayerDto player : team) {
            PlayerSnapshot snapshot = player.playerId() == null ? null : byId.get(player.playerId());
            String race = snapshot == null ? "R" : snapshot.race();
            counts.merge(race, 1, Integer::sum);
        }
        return counts;
    }

    private int calculateRepeatTeammatePenalty(
        TeamBalancingService.BalanceCandidate candidate,
        Map<String, Integer> teammatePairCounts
    ) {
        return countTeammatePairs(candidate.homeTeam(), teammatePairCounts)
            + countTeammatePairs(candidate.awayTeam(), teammatePairCounts);
    }

    private int countTeammatePairs(List<BalancePlayerDto> team, Map<String, Integer> teammatePairCounts) {
        int penalty = 0;
        for (int left = 0; left < team.size(); left++) {
            for (int right = left + 1; right < team.size(); right++) {
                Long leftId = team.get(left).playerId();
                Long rightId = team.get(right).playerId();
                if (leftId == null || rightId == null) {
                    continue;
                }
                penalty += teammatePairCounts.getOrDefault(pairKey(leftId, rightId), 0);
            }
        }
        return penalty;
    }

    private int calculateRepeatMatchupPenalty(
        TeamBalancingService.BalanceCandidate candidate,
        Map<String, Integer> opponentPairCounts,
        Map<String, Integer> teamSignatureCounts,
        Map<String, Integer> matchupSignatureCounts
    ) {
        int penalty = 0;
        penalty += countOpponentPairs(candidate.homeTeam(), candidate.awayTeam(), opponentPairCounts);

        String homeSignature = teamSignature(candidate.homeTeam());
        String awaySignature = teamSignature(candidate.awayTeam());
        penalty += teamSignatureCounts.getOrDefault(homeSignature, 0);
        penalty += teamSignatureCounts.getOrDefault(awaySignature, 0);
        penalty += matchupSignatureCounts.getOrDefault(matchupSignature(homeSignature, awaySignature), 0);
        return penalty;
    }

    private int countOpponentPairs(
        List<BalancePlayerDto> homeTeam,
        List<BalancePlayerDto> awayTeam,
        Map<String, Integer> opponentPairCounts
    ) {
        int penalty = 0;
        for (BalancePlayerDto home : homeTeam) {
            for (BalancePlayerDto away : awayTeam) {
                if (home.playerId() == null || away.playerId() == null) {
                    continue;
                }
                penalty += opponentPairCounts.getOrDefault(pairKey(home.playerId(), away.playerId()), 0);
            }
        }
        return penalty;
    }

    private int calculateInterMatchPenalty(
        TeamBalancingService.BalanceCandidate candidate,
        List<MatchEvaluation> selectedSoFar
    ) {
        if (selectedSoFar.isEmpty()) {
            return 0;
        }
        int teamSize = candidate.teamSize();
        double candidateAverage = (candidate.homeMmr() + candidate.awayMmr()) / (double) (teamSize * 2);
        double existingAverage = selectedSoFar.stream()
            .mapToDouble(MatchEvaluation::groupAverage)
            .average()
            .orElse(candidateAverage);
        return (int) Math.round(Math.abs(candidateAverage - existingAverage));
    }

    private String buildRaceSummary(List<BalancePlayerDto> team, Map<Long, PlayerSnapshot> byId) {
        List<String> races = new ArrayList<>();
        for (BalancePlayerDto player : team) {
            PlayerSnapshot snapshot = player.playerId() == null ? null : byId.get(player.playerId());
            races.add(snapshot == null ? "R" : snapshot.race());
        }
        races.sort(Comparator.naturalOrder());
        return String.join("", races);
    }

    private Map<Long, PlayerSnapshot> indexById(List<PlayerSnapshot> players) {
        Map<Long, PlayerSnapshot> byId = new LinkedHashMap<>();
        for (PlayerSnapshot player : players) {
            byId.put(player.id(), player);
        }
        return byId;
    }

    private WeightProfile resolveWeightProfile(MultiBalanceMode mode) {
        if (mode == MultiBalanceMode.DIVERSITY_FIRST) {
            return new WeightProfile(
                mmrDiffWeight * 0.9,
                interMatchBalanceWeight * 0.8,
                repeatTeammatePenaltyWeight * 4.0,
                repeatMatchupPenaltyWeight * 4.0,
                racePenaltyWeight * 1.5
            );
        }
        if (mode == MultiBalanceMode.RACE_DISTRIBUTION_FIRST) {
            return new WeightProfile(
                mmrDiffWeight * 0.9,
                interMatchBalanceWeight * 0.8,
                repeatTeammatePenaltyWeight * 2.0,
                repeatMatchupPenaltyWeight * 2.0,
                racePenaltyWeight * 5.0
            );
        }
        return new WeightProfile(
            mmrDiffWeight,
            interMatchBalanceWeight,
            repeatTeammatePenaltyWeight,
            repeatMatchupPenaltyWeight,
            racePenaltyWeight
        );
    }

    private RecentHistory loadRecentHistory(Long groupId) {
        List<Match> recentMatches = matchRepository.findRecentByGroupId(
            groupId,
            PageRequest.of(0, recentMatchLookbackCount)
        );

        Map<String, Integer> teammatePairCounts = new HashMap<>();
        Map<String, Integer> opponentPairCounts = new HashMap<>();
        Map<String, Integer> teamSignatureCounts = new HashMap<>();
        Map<String, Integer> matchupSignatureCounts = new HashMap<>();

        for (Match match : recentMatches) {
            List<MatchParticipant> participants = matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId());
            List<Long> homeIds = participants.stream()
                .filter(participant -> TEAM_HOME.equals(normalizeTeam(participant.getTeam())))
                .map(participant -> participant.getPlayer() == null ? null : participant.getPlayer().getId())
                .filter(Objects::nonNull)
                .toList();
            List<Long> awayIds = participants.stream()
                .filter(participant -> TEAM_AWAY.equals(normalizeTeam(participant.getTeam())))
                .map(participant -> participant.getPlayer() == null ? null : participant.getPlayer().getId())
                .filter(Objects::nonNull)
                .toList();

            if (homeIds.isEmpty() || awayIds.isEmpty()) {
                continue;
            }

            accumulateTeamPairs(homeIds, teammatePairCounts);
            accumulateTeamPairs(awayIds, teammatePairCounts);
            accumulateOpponentPairs(homeIds, awayIds, opponentPairCounts);

            String homeSignature = teamSignatureFromIds(homeIds);
            String awaySignature = teamSignatureFromIds(awayIds);
            teamSignatureCounts.merge(homeSignature, 1, Integer::sum);
            teamSignatureCounts.merge(awaySignature, 1, Integer::sum);
            matchupSignatureCounts.merge(matchupSignature(homeSignature, awaySignature), 1, Integer::sum);
        }

        return new RecentHistory(
            teammatePairCounts,
            opponentPairCounts,
            teamSignatureCounts,
            matchupSignatureCounts
        );
    }

    private void accumulateTeamPairs(List<Long> teamIds, Map<String, Integer> counts) {
        for (int left = 0; left < teamIds.size(); left++) {
            for (int right = left + 1; right < teamIds.size(); right++) {
                counts.merge(pairKey(teamIds.get(left), teamIds.get(right)), 1, Integer::sum);
            }
        }
    }

    private void accumulateOpponentPairs(List<Long> homeIds, List<Long> awayIds, Map<String, Integer> counts) {
        for (Long homeId : homeIds) {
            for (Long awayId : awayIds) {
                counts.merge(pairKey(homeId, awayId), 1, Integer::sum);
            }
        }
    }

    private String teamSignature(List<BalancePlayerDto> team) {
        List<Long> ids = team.stream()
            .map(BalancePlayerDto::playerId)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        return teamSignatureFromIds(ids);
    }

    private String teamSignatureFromIds(List<Long> ids) {
        return ids.stream().sorted().map(String::valueOf).reduce((left, right) -> left + "-" + right).orElse("");
    }

    private String matchupSignature(String teamOne, String teamTwo) {
        if (teamOne.compareTo(teamTwo) <= 0) {
            return teamOne + "|" + teamTwo;
        }
        return teamTwo + "|" + teamOne;
    }

    private String pairKey(Long left, Long right) {
        long first = Math.min(left, right);
        long second = Math.max(left, right);
        return first + ":" + second;
    }

    private String normalizeTeam(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRace(String race) {
        if (race == null || race.isBlank()) {
            return "R";
        }
        return race.trim().toUpperCase(Locale.ROOT);
    }

    private int safeMmr(Integer mmr) {
        return mmr == null ? 0 : mmr;
    }

    private record PlayerSnapshot(
        Long id,
        String nickname,
        int mmr,
        String race
    ) {
    }

    private static class MatchGroup {
        private final int index;
        private final int capacity;
        private final List<PlayerSnapshot> players = new ArrayList<>();
        private int totalMmr = 0;

        private MatchGroup(int index, int teamSize) {
            this.index = index;
            this.capacity = teamSize * 2;
        }

        private int index() {
            return index;
        }

        private int capacity() {
            return capacity;
        }

        private List<PlayerSnapshot> players() {
            return players;
        }
    }

    private record MatchAllocationPlan(
        List<Integer> teamSizes,
        int assignedPlayers,
        int waitingPlayers
    ) {
    }

    private record WeightProfile(
        double mmrDiffWeight,
        double interMatchWeight,
        double repeatTeammateWeight,
        double repeatMatchupWeight,
        double racePenaltyWeight
    ) {
    }

    private record RecentHistory(
        Map<String, Integer> teammatePairCounts,
        Map<String, Integer> opponentPairCounts,
        Map<String, Integer> teamSignatureCounts,
        Map<String, Integer> matchupSignatureCounts
    ) {
    }

    private record MatchEvaluation(
        MultiBalanceMatchResponse response,
        double groupAverage
    ) {
    }

    private record CandidateScore(
        TeamBalancingService.BalanceCandidate candidate,
        double score,
        int racePenalty,
        int repeatTeammatePenalty,
        int repeatMatchupPenalty,
        int interMatchPenalty
    ) {
        private boolean isBetterThan(CandidateScore other) {
            if (other == null) {
                return true;
            }
            if (score != other.score) {
                return score < other.score;
            }
            if (candidate.mmrDiff() != other.candidate.mmrDiff()) {
                return candidate.mmrDiff() < other.candidate.mmrDiff();
            }
            if (repeatTeammatePenalty != other.repeatTeammatePenalty) {
                return repeatTeammatePenalty < other.repeatTeammatePenalty;
            }
            if (repeatMatchupPenalty != other.repeatMatchupPenalty) {
                return repeatMatchupPenalty < other.repeatMatchupPenalty;
            }
            if (racePenalty != other.racePenalty) {
                return racePenalty < other.racePenalty;
            }
            return interMatchPenalty < other.interMatchPenalty;
        }
    }
}
