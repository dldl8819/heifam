package com.balancify.backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RaceCompositionPolicy {

    private static final Map<Integer, List<String>> ALLOWED_BY_TEAM_SIZE = Map.of(
        2, List.of("PP", "PT", "PZ"),
        3, List.of("PPP", "PPT", "PPZ", "PTZ")
    );

    private RaceCompositionPolicy() {
    }

    public static String normalizeForTeamSize(String value, int teamSize) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        List<String> allowed = ALLOWED_BY_TEAM_SIZE.get(teamSize);
        if (allowed == null || !allowed.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 종족 조합입니다.");
        }
        return normalized;
    }

    public static String normalizeForAnyTeamSize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized.length()) {
            case 2 -> normalizeForTeamSize(normalized, 2);
            case 3 -> normalizeForTeamSize(normalized, 3);
            default -> throw new IllegalArgumentException("지원하지 않는 종족 조합입니다.");
        };
    }

    public static boolean matches(List<String> teamRaces, String raceComposition) {
        if (raceComposition == null || raceComposition.isBlank()) {
            return true;
        }
        return raceComposition.equals(canonicalize(teamRaces));
    }

    public static String canonicalize(List<String> teamRaces) {
        if (teamRaces == null || teamRaces.isEmpty()) {
            throw new IllegalArgumentException("선택한 종족 조합으로 매치를 구성할 수 없습니다.");
        }

        return teamRaces.stream()
            .map(PlayerRacePolicy::normalizeAssignedRace)
            .sorted(Comparator.naturalOrder())
            .reduce("", String::concat);
    }

    public static String normalizePlayerRace(String race) {
        return PlayerRacePolicy.normalizeCapability(race);
    }
}
