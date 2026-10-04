package com.balancify.backend.service;

import com.balancify.backend.domain.MatchSeriesFormat;
import java.util.List;

/**
 * How two tournament teams play their series. When both teams can field PPT or PPZ, the series is
 * PPP, PPT and PPZ with all three games played, and a game the teams cannot both field becomes
 * PPP. Otherwise it is PPP, best of three. Each team keeps its three players for every game; who
 * takes Terran or Zerg follows the usual race assignment.
 */
public final class TournamentSeriesPlanner {

    public static final int GAMES_PER_SERIES = 3;

    private static final List<String> MIXED_COMPOSITIONS = List.of("PPP", "PPT", "PPZ");
    private static final List<String> BEST_OF_THREE_COMPOSITIONS = List.of("PPP", "PPP", "PPP");
    // Every three-player composition, in the order a stand-in is chosen.
    private static final List<String> COMPOSITIONS = List.of("PPP", "PPT", "PPZ", "PTZ");

    private TournamentSeriesPlanner() {
    }

    public record SeriesPlan(MatchSeriesFormat format, List<String> compositions) {
    }

    /** Capabilities are race strings such as P, PT or PTZ, one per player. */
    public static SeriesPlan plan(List<String> homeCapabilities, List<String> awayCapabilities) {
        List<String> shared = sharedCompositions(homeCapabilities, awayCapabilities);
        if (shared.isEmpty()) {
            throw new IllegalArgumentException("두 팀이 함께 할 수 있는 종족 조합이 없습니다.");
        }

        boolean mixed = shared.contains("PPT") || shared.contains("PPZ");
        String standIn = shared.getFirst();
        List<String> compositions = (mixed ? MIXED_COMPOSITIONS : BEST_OF_THREE_COMPOSITIONS).stream()
            .map(composition -> shared.contains(composition) ? composition : standIn)
            .toList();
        return new SeriesPlan(mixed ? MatchSeriesFormat.MIXED_THREE : MatchSeriesFormat.BEST_OF_THREE, compositions);
    }

    public static List<String> sharedCompositions(List<String> homeCapabilities, List<String> awayCapabilities) {
        return COMPOSITIONS.stream()
            .filter(composition -> canPlay(homeCapabilities, composition) && canPlay(awayCapabilities, composition))
            .toList();
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
}
