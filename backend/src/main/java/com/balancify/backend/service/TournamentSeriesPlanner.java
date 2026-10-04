package com.balancify.backend.service;

import com.balancify.backend.domain.MatchSeriesFormat;
import java.util.List;
import java.util.Map;

/**
 * How two teams play a series, in a team tournament or after a multi-balance. When both teams can
 * field a Terran or a Zerg game (PPT or PPZ for three players, PT or PZ for two), the series is
 * the all-Protoss game, the Terran game and the Zerg game with all three played, and a game the
 * teams cannot both field becomes the all-Protoss one. Otherwise it is the all-Protoss game, best
 * of three. Each team keeps its players for every game; who takes Terran or Zerg follows the
 * usual race assignment.
 */
public final class TournamentSeriesPlanner {

    public static final int GAMES_PER_SERIES = 3;

    // Every composition per team size, in the order a stand-in is chosen.
    private static final Map<Integer, List<String>> COMPOSITIONS = Map.of(
        2, List.of("PP", "PT", "PZ"),
        3, List.of("PPP", "PPT", "PPZ", "PTZ")
    );

    private TournamentSeriesPlanner() {
    }

    public record SeriesPlan(MatchSeriesFormat format, List<String> compositions) {
    }

    /** Capabilities are race strings such as P, PT or PTZ, one per player; both teams the same size. */
    public static SeriesPlan plan(List<String> homeCapabilities, List<String> awayCapabilities) {
        List<String> shared = sharedCompositions(homeCapabilities, awayCapabilities);
        if (shared.isEmpty()) {
            throw new IllegalArgumentException("두 팀이 함께 할 수 있는 종족 조합이 없습니다.");
        }

        int teamSize = homeCapabilities.size();
        List<String> mixedGames = mixedGames(teamSize);
        boolean mixed = shared.contains(mixedGames.get(1)) || shared.contains(mixedGames.get(2));
        String allProtoss = mixedGames.getFirst();
        String standIn = shared.getFirst();
        List<String> compositions = (mixed ? mixedGames : List.of(allProtoss, allProtoss, allProtoss)).stream()
            .map(composition -> shared.contains(composition) ? composition : standIn)
            .toList();
        return new SeriesPlan(mixed ? MatchSeriesFormat.MIXED_THREE : MatchSeriesFormat.BEST_OF_THREE, compositions);
    }

    /**
     * The plan in the format asked for, or the usual one when none is asked. Teams that could mix
     * may still play the all-Protoss game best of three; mixing needs a Terran or Zerg game both
     * teams can field.
     */
    public static SeriesPlan plan(
        List<String> homeCapabilities,
        List<String> awayCapabilities,
        MatchSeriesFormat requested
    ) {
        SeriesPlan plan = plan(homeCapabilities, awayCapabilities);
        if (requested == null || requested == plan.format()) {
            return plan;
        }
        if (requested == MatchSeriesFormat.MIXED_THREE) {
            throw new IllegalArgumentException("두 팀이 함께 할 수 있는 테란·저그 판이 없어 섞어서 할 수 없습니다.");
        }
        String allProtoss = mixedGames(homeCapabilities.size()).getFirst();
        if (!sharedCompositions(homeCapabilities, awayCapabilities).contains(allProtoss)) {
            throw new IllegalArgumentException("두 팀이 프로토스로만 할 수 없어 " + allProtoss + " 3판을 할 수 없습니다.");
        }
        return new SeriesPlan(MatchSeriesFormat.BEST_OF_THREE, List.of(allProtoss, allProtoss, allProtoss));
    }

    public static List<String> sharedCompositions(List<String> homeCapabilities, List<String> awayCapabilities) {
        if (homeCapabilities.size() != awayCapabilities.size()) {
            throw new IllegalArgumentException("두 팀의 인원이 같아야 합니다.");
        }
        List<String> compositions = COMPOSITIONS.get(homeCapabilities.size());
        if (compositions == null) {
            throw new IllegalArgumentException("시리즈는 2:2나 3:3으로만 할 수 있습니다.");
        }
        return compositions.stream()
            .filter(composition -> canPlay(homeCapabilities, composition) && canPlay(awayCapabilities, composition))
            .toList();
    }

    /** How many of the Terran and Zerg games (0 to 2) two teams can both field. */
    public static int sharedOffRaceGames(List<String> homeCapabilities, List<String> awayCapabilities) {
        List<String> shared = sharedCompositions(homeCapabilities, awayCapabilities);
        List<String> mixedGames = mixedGames(homeCapabilities.size());
        return (shared.contains(mixedGames.get(1)) ? 1 : 0) + (shared.contains(mixedGames.get(2)) ? 1 : 0);
    }

    public static boolean canPlay(List<String> capabilities, String composition) {
        return assignRaces(capabilities, composition) != null;
    }

    /** The race each player takes for a composition, in the players' order; null when the team cannot field it. */
    public static List<String> assignRaces(List<String> capabilities, String composition) {
        PlayerRacePolicy.TeamRaceAssignment assignment = PlayerRacePolicy.assignToComposition(capabilities, composition);
        return assignment == null ? null : assignment.assignedRaces();
    }

    public static boolean isOffRaceCapable(String capability) {
        return capability.contains("T") || capability.contains("Z");
    }

    /** Best of three ends at two wins; a mixed series always plays its three games. */
    public static boolean isDecided(MatchSeriesFormat format, int homeWins, int awayWins, int gamesPlayed) {
        if (format == MatchSeriesFormat.BEST_OF_THREE) {
            return homeWins >= 2 || awayWins >= 2;
        }
        return gamesPlayed >= GAMES_PER_SERIES;
    }

    public static String capabilityOf(String race) {
        return PlayerRacePolicy.normalizeCapabilityOrDefault(race, "P");
    }

    // The all-Protoss game, then one player on Terran, then one on Zerg.
    private static List<String> mixedGames(int teamSize) {
        String protoss = "P".repeat(teamSize - 1);
        return List.of(protoss + "P", protoss + "T", protoss + "Z");
    }
}
