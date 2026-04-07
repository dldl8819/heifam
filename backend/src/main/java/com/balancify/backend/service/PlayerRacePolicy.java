package com.balancify.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlayerRacePolicy {

    private static final List<String> RACE_ORDER = List.of("P", "T", "Z");
    private static final Set<String> ASSIGNED_RACES = Set.of("P", "T", "Z");

    private PlayerRacePolicy() {
    }

    public static String normalizeCapability(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("선수 종족 정보가 올바르지 않습니다.");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        LinkedHashSet<String> races = new LinkedHashSet<>();
        for (char race : normalized.toCharArray()) {
            String token = String.valueOf(race);
            if (!ASSIGNED_RACES.contains(token)) {
                throw new IllegalArgumentException("선수 종족 정보가 올바르지 않습니다.");
            }
            races.add(token);
        }

        if (races.isEmpty() || races.size() > 3) {
            throw new IllegalArgumentException("선수 종족 정보가 올바르지 않습니다.");
        }

        return RACE_ORDER.stream()
            .filter(races::contains)
            .reduce("", String::concat);
    }

    public static String normalizeCapabilityOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return normalizeCapability(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static String normalizeAssignedRace(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ASSIGNED_RACES.contains(normalized)) {
            throw new IllegalArgumentException("배정 종족 정보가 올바르지 않습니다.");
        }
        return normalized;
    }

    public static List<String> playableRaces(String capability) {
        String normalized = normalizeCapability(capability);
        List<String> playable = new ArrayList<>(normalized.length());
        for (char race : normalized.toCharArray()) {
            playable.add(String.valueOf(race));
        }
        return List.copyOf(playable);
    }

    public static String toDisplayRace(String capability) {
        return normalizeCapabilityOrDefault(capability, "P");
    }

    public static String primaryRace(String capability) {
        List<String> playable = playableRaces(capability);
        return playable.isEmpty() ? "P" : playable.getFirst();
    }

    public static TeamRaceAssignment assignToComposition(List<String> capabilities, String raceComposition) {
        if (raceComposition == null || raceComposition.isBlank()) {
            return new TeamRaceAssignment(
                capabilities.stream().map(PlayerRacePolicy::primaryRace).toList()
            );
        }

        List<String> slots = raceComposition.chars()
            .mapToObj(race -> String.valueOf((char) race))
            .toList();
        List<PlayerCapability> orderedPlayers = new ArrayList<>();
        for (int index = 0; index < capabilities.size(); index++) {
            List<String> playable = playableRaces(capabilities.get(index));
            orderedPlayers.add(new PlayerCapability(index, playable));
        }

        orderedPlayers.sort((left, right) -> {
            if (left.playableRaces().size() != right.playableRaces().size()) {
                return Integer.compare(left.playableRaces().size(), right.playableRaces().size());
            }
            String leftValue = String.join("", left.playableRaces());
            String rightValue = String.join("", right.playableRaces());
            int compare = leftValue.compareTo(rightValue);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(left.originalIndex(), right.originalIndex());
        });

        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (String slot : slots) {
            remaining.merge(slot, 1, Integer::sum);
        }

        String[] assigned = new String[capabilities.size()];
        if (!assignPlayer(0, orderedPlayers, remaining, assigned)) {
            return null;
        }

        return new TeamRaceAssignment(List.of(assigned));
    }

    private static boolean assignPlayer(
        int index,
        List<PlayerCapability> orderedPlayers,
        Map<String, Integer> remaining,
        String[] assigned
    ) {
        if (index >= orderedPlayers.size()) {
            return remaining.values().stream().allMatch(value -> value == 0);
        }

        PlayerCapability player = orderedPlayers.get(index);
        List<String> options = player.playableRaces().stream()
            .filter(race -> remaining.getOrDefault(race, 0) > 0)
            .sorted((left, right) -> {
                int leftRemaining = remaining.getOrDefault(left, 0);
                int rightRemaining = remaining.getOrDefault(right, 0);
                if (leftRemaining != rightRemaining) {
                    return Integer.compare(leftRemaining, rightRemaining);
                }
                return left.compareTo(right);
            })
            .toList();

        for (String option : options) {
            assigned[player.originalIndex()] = option;
            remaining.put(option, remaining.get(option) - 1);
            if (assignPlayer(index + 1, orderedPlayers, remaining, assigned)) {
                return true;
            }
            remaining.put(option, remaining.get(option) + 1);
            assigned[player.originalIndex()] = null;
        }

        return false;
    }

    private record PlayerCapability(
        int originalIndex,
        List<String> playableRaces
    ) {
    }

    public record TeamRaceAssignment(
        List<String> assignedRaces
    ) {
    }
}
