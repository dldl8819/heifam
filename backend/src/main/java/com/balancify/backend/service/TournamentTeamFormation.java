package com.balancify.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits players into three-player teams for a team tournament: 6 to 8 players make two teams,
 * 9 to 11 three and 12 to 14 four, and the one or two left over sit out. Every split is compared
 * (15,400 for twelve players): the closest team MMR totals win, with players who can take Terran
 * or Zerg spread across the teams. Players who sat out of the last tournament are always placed.
 */
final class TournamentTeamFormation {

    static final int TEAM_SIZE = 3;
    // Score per step of uneven spread, in MMR: team totals may drift this far apart to keep the
    // players who can take Terran or Zerg on different teams.
    private static final long OFF_RACE_SPREAD_WEIGHT = 40;
    private static final long RACE_SPREAD_WEIGHT = 10;
    // A team that cannot play PPP is only formed when no other split works.
    private static final long NO_PPP_WEIGHT = 100_000;

    private TournamentTeamFormation() {
    }

    record Candidate(Long playerId, int mmr, String capability) {
    }

    record Formation(List<List<Candidate>> teams, List<Candidate> waiting) {
    }

    static int teamCountFor(int playerCount) {
        if (playerCount < 6 || playerCount > 14) {
            throw new IllegalArgumentException(
                "팀 토너먼트는 6~14명으로 만들 수 있습니다(6~8명 2팀, 9~11명 3팀, 12~14명 4팀)."
            );
        }
        return playerCount / TEAM_SIZE;
    }

    static Formation form(List<Candidate> players, Set<Long> mustPlayIds) {
        int teamCount = teamCountFor(players.size());
        List<Candidate> ordered = players.stream()
            .sorted(Comparator.comparing(Candidate::playerId))
            .toList();
        int waitingCount = ordered.size() - teamCount * TEAM_SIZE;
        List<Candidate> mayWait = ordered.stream()
            .filter(player -> !mustPlayIds.contains(player.playerId()))
            .toList();
        if (mayWait.size() < waitingCount) {
            mayWait = ordered;
        }

        Search search = new Search(ordered);
        for (List<Candidate> waiting : combinations(mayWait, waitingCount)) {
            Set<Long> waitingIds = new HashSet<>();
            waiting.forEach(player -> waitingIds.add(player.playerId()));
            List<Candidate> playing = ordered.stream()
                .filter(player -> !waitingIds.contains(player.playerId()))
                .toList();
            search.partition(playing, new ArrayList<>(), waiting, Long.MAX_VALUE, Long.MIN_VALUE);
        }
        if (search.best == null) {
            throw new IllegalArgumentException("모든 팀이 서로 함께 할 수 있는 종족 조합으로 팀을 나눌 수 없습니다.");
        }
        return search.best;
    }

    /**
     * Teams in bracket order, pairing the closest totals. Four teams: semifinals 1 v 2 and 3 v 4.
     * Three teams: 1 v 2 in the semifinal while 3 waits in the final. Two teams stay as they are.
     */
    static List<List<Candidate>> bracketOrder(List<List<Candidate>> teams) {
        if (teams.size() == 3) {
            return threeTeamOrder(teams);
        }
        if (teams.size() != 4) {
            return teams;
        }
        int[][] pairings = {{0, 1, 2, 3}, {0, 2, 1, 3}, {0, 3, 1, 2}};
        int[] best = pairings[0];
        long bestMaxGap = Long.MAX_VALUE;
        long bestTotalGap = Long.MAX_VALUE;
        for (int[] pairing : pairings) {
            long firstGap = Math.abs(totalMmr(teams.get(pairing[0])) - totalMmr(teams.get(pairing[1])));
            long secondGap = Math.abs(totalMmr(teams.get(pairing[2])) - totalMmr(teams.get(pairing[3])));
            long maxGap = Math.max(firstGap, secondGap);
            long totalGap = firstGap + secondGap;
            if (maxGap < bestMaxGap || (maxGap == bestMaxGap && totalGap < bestTotalGap)) {
                best = pairing;
                bestMaxGap = maxGap;
                bestTotalGap = totalGap;
            }
        }
        return List.of(teams.get(best[0]), teams.get(best[1]), teams.get(best[2]), teams.get(best[3]));
    }

    private static List<List<Candidate>> threeTeamOrder(List<List<Candidate>> teams) {
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 2, 0}};
        int[] best = orders[0];
        long bestGap = Long.MAX_VALUE;
        for (int[] order : orders) {
            long gap = Math.abs(totalMmr(teams.get(order[0])) - totalMmr(teams.get(order[1])));
            if (gap < bestGap) {
                best = order;
                bestGap = gap;
            }
        }
        return List.of(teams.get(best[0]), teams.get(best[1]), teams.get(best[2]));
    }

    static long totalMmr(List<Candidate> team) {
        return team.stream().mapToLong(Candidate::mmr).sum();
    }

    private static List<List<Candidate>> combinations(List<Candidate> source, int size) {
        List<List<Candidate>> result = new ArrayList<>();
        collectCombinations(source, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void collectCombinations(
        List<Candidate> source,
        int size,
        int start,
        List<Candidate> current,
        List<List<Candidate>> result
    ) {
        if (current.size() == size) {
            result.add(List.copyOf(current));
            return;
        }
        for (int index = start; index <= source.size() - (size - current.size()); index++) {
            current.add(source.get(index));
            collectCombinations(source, size, index + 1, current, result);
            current.removeLast();
        }
    }

    private static final class Search {

        private final Map<Long, Integer> indexById = new HashMap<>();
        private final Map<Long, Set<String>> playableByTeamMask = new HashMap<>();
        private Formation best;
        private long bestScore = Long.MAX_VALUE;
        private List<Long> bestKey;

        private Search(List<Candidate> players) {
            for (int index = 0; index < players.size(); index++) {
                indexById.put(players.get(index).playerId(), index);
            }
        }

        // The first remaining player anchors each team, so every split is visited exactly once.
        // A split is dropped as soon as its team totals already lie further apart than the best
        // score so far, since the other terms of a score only add to that gap.
        private void partition(
            List<Candidate> remaining,
            List<List<Candidate>> teams,
            List<Candidate> waiting,
            long minTotal,
            long maxTotal
        ) {
            if (remaining.isEmpty()) {
                evaluate(teams, waiting);
                return;
            }
            Candidate anchor = remaining.getFirst();
            long remainingTotal = totalMmr(remaining);
            int teamsLeft = remaining.size() / TEAM_SIZE;
            for (int first = 1; first < remaining.size(); first++) {
                for (int second = first + 1; second < remaining.size(); second++) {
                    long total = anchor.mmr() + remaining.get(first).mmr() + remaining.get(second).mmr();
                    long nextMin = Math.min(minTotal, total);
                    long nextMax = Math.max(maxTotal, total);
                    if (best != null && lowestSpread(nextMin, nextMax, remainingTotal - total, teamsLeft - 1) > bestScore) {
                        continue;
                    }
                    List<Candidate> rest = new ArrayList<>(remaining.size() - TEAM_SIZE);
                    for (int index = 1; index < remaining.size(); index++) {
                        if (index != first && index != second) {
                            rest.add(remaining.get(index));
                        }
                    }
                    teams.add(List.of(anchor, remaining.get(first), remaining.get(second)));
                    partition(rest, teams, waiting, nextMin, nextMax);
                    teams.removeLast();
                }
            }
        }

        // The teams still to form average the players left, so the final totals reach at least that far.
        private static double lowestSpread(long minTotal, long maxTotal, long totalLeft, int teamsLeft) {
            if (teamsLeft == 0) {
                return maxTotal - minTotal;
            }
            double averageLeft = (double) totalLeft / teamsLeft;
            return Math.max(maxTotal, averageLeft) - Math.min(minTotal, averageLeft);
        }

        private void evaluate(List<List<Candidate>> teams, List<Candidate> waiting) {
            long minTotal = Long.MAX_VALUE;
            long maxTotal = Long.MIN_VALUE;
            int minOffRace = Integer.MAX_VALUE;
            int maxOffRace = Integer.MIN_VALUE;
            int minTerran = Integer.MAX_VALUE;
            int maxTerran = Integer.MIN_VALUE;
            int minZerg = Integer.MAX_VALUE;
            int maxZerg = Integer.MIN_VALUE;
            int teamsWithoutPpp = 0;
            List<Set<String>> playable = new ArrayList<>(teams.size());

            for (List<Candidate> team : teams) {
                long total = totalMmr(team);
                int offRace = 0;
                int terran = 0;
                int zerg = 0;
                for (Candidate player : team) {
                    if (TournamentSeriesPlanner.isOffRaceCapable(player.capability())) {
                        offRace++;
                    }
                    if (player.capability().contains("T")) {
                        terran++;
                    }
                    if (player.capability().contains("Z")) {
                        zerg++;
                    }
                }
                minTotal = Math.min(minTotal, total);
                maxTotal = Math.max(maxTotal, total);
                minOffRace = Math.min(minOffRace, offRace);
                maxOffRace = Math.max(maxOffRace, offRace);
                minTerran = Math.min(minTerran, terran);
                maxTerran = Math.max(maxTerran, terran);
                minZerg = Math.min(minZerg, zerg);
                maxZerg = Math.max(maxZerg, zerg);

                Set<String> teamPlayable = playable(team);
                if (!teamPlayable.contains("PPP")) {
                    teamsWithoutPpp++;
                }
                playable.add(teamPlayable);
            }

            // Any two teams may meet somewhere in the bracket, so every pair needs a shared composition.
            for (int left = 0; left < playable.size(); left++) {
                for (int right = left + 1; right < playable.size(); right++) {
                    if (Collections.disjoint(playable.get(left), playable.get(right))) {
                        return;
                    }
                }
            }

            long score = (maxTotal - minTotal)
                + OFF_RACE_SPREAD_WEIGHT * (maxOffRace - minOffRace)
                + RACE_SPREAD_WEIGHT * ((maxTerran - minTerran) + (maxZerg - minZerg))
                + NO_PPP_WEIGHT * teamsWithoutPpp;
            if (best != null && score > bestScore) {
                return;
            }
            // Equal scores fall back to player ids, so the same players always give the same teams.
            List<Long> key = new ArrayList<>();
            teams.forEach(team -> team.forEach(player -> key.add(player.playerId())));
            waiting.forEach(player -> key.add(player.playerId()));
            if (best == null || score < bestScore || compareKeys(key, bestKey) < 0) {
                best = new Formation(List.copyOf(teams), waiting);
                bestScore = score;
                bestKey = key;
            }
        }

        private Set<String> playable(List<Candidate> team) {
            long mask = 0;
            for (Candidate player : team) {
                mask |= 1L << indexById.get(player.playerId());
            }
            return playableByTeamMask.computeIfAbsent(mask, ignored -> {
                List<String> capabilities = team.stream().map(Candidate::capability).toList();
                Set<String> compositions = new HashSet<>();
                for (String composition : List.of("PPP", "PPT", "PPZ", "PTZ")) {
                    if (TournamentSeriesPlanner.canPlay(capabilities, composition)) {
                        compositions.add(composition);
                    }
                }
                return compositions;
            });
        }

        private static int compareKeys(List<Long> left, List<Long> right) {
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                int compare = Long.compare(left.get(index), right.get(index));
                if (compare != 0) {
                    return compare;
                }
            }
            return Integer.compare(left.size(), right.size());
        }
    }
}
