package com.balancify.backend.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerTierPolicy {

    private static final String TIER_NONE = "NONE";
    private static final List<String> ORDERED_TIERS = List.of(
        TIER_NONE, "C-", "C", "C+", "B-", "B", "B+", "A-", "A", "A+", "S"
    );
    private static final Map<String, Integer> TIER_INDEX = Map.ofEntries(
        Map.entry(TIER_NONE, 0),
        Map.entry("C-", 1),
        Map.entry("C", 2),
        Map.entry("C+", 3),
        Map.entry("B-", 4),
        Map.entry("B", 5),
        Map.entry("B+", 6),
        Map.entry("A-", 7),
        Map.entry("A", 8),
        Map.entry("A+", 9),
        Map.entry("S", 10)
    );
    private static final Map<String, Integer> TIER_FLOOR_MMR = Map.ofEntries(
        Map.entry(TIER_NONE, 0),
        Map.entry("C-", 1),
        Map.entry("C", 200),
        Map.entry("C+", 300),
        Map.entry("B-", 400),
        Map.entry("B", 500),
        Map.entry("B+", 600),
        Map.entry("A-", 700),
        Map.entry("A", 800),
        Map.entry("A+", 900),
        Map.entry("S", 1000)
    );
    private static final int PLACEMENT_GAME_COUNT = 5;
    private static final int PROMOTION_MMR_BUFFER = 30;
    private static final int DEMOTION_MMR_BUFFER = 50;

    private PlayerTierPolicy() {
    }

    public static String resolveTier(Integer mmr) {
        int normalizedMmr = mmr == null ? 0 : mmr;
        if (normalizedMmr <= 0) {
            return TIER_NONE;
        }

        if (normalizedMmr < 200) {
            return "C-";
        }
        if (normalizedMmr < 300) {
            return "C";
        }
        if (normalizedMmr < 400) {
            return "C+";
        }
        if (normalizedMmr < 500) {
            return "B-";
        }
        if (normalizedMmr < 600) {
            return "B";
        }
        if (normalizedMmr < 700) {
            return "B+";
        }
        if (normalizedMmr < 800) {
            return "A-";
        }
        if (normalizedMmr < 900) {
            return "A";
        }
        if (normalizedMmr < 1000) {
            return "A+";
        }
        return "S";
    }

    public static String resolveTierForRankedMatch(
        String currentTier,
        Integer mmr,
        int completedRankedGames
    ) {
        int normalizedMmr = mmr == null ? 0 : mmr;
        String targetTier = resolveTier(normalizedMmr);
        if (normalizedMmr <= 0) {
            return TIER_NONE;
        }

        String normalizedCurrentTier = canonicalTier(currentTier, targetTier);
        int currentTierIndex = tierIndex(normalizedCurrentTier);
        int targetTierIndex = tierIndex(targetTier);

        if (targetTierIndex > currentTierIndex && canPromote(normalizedCurrentTier, normalizedMmr)) {
            return stepTier(normalizedCurrentTier, 1);
        }

        if (Math.max(0, completedRankedGames) < PLACEMENT_GAME_COUNT) {
            return normalizedCurrentTier;
        }

        if (targetTierIndex < currentTierIndex && canDemote(normalizedCurrentTier, normalizedMmr)) {
            return stepTier(normalizedCurrentTier, -1);
        }

        return normalizedCurrentTier;
    }

    public static String resolveTierForSnapshot(String tier, Integer mmr) {
        return canonicalTier(tier, resolveTier(mmr));
    }

    public static String demoteTier(String tier, int steps) {
        if (steps <= 0) {
            return canonicalTier(tier, TIER_NONE);
        }

        String normalizedTier = canonicalTier(tier, TIER_NONE);
        return stepTier(normalizedTier, -steps);
    }

    public static boolean isLowTier(Integer mmr) {
        String tier = resolveTier(mmr);
        return TIER_NONE.equals(tier) || "C+".equals(tier) || "C".equals(tier) || "C-".equals(tier);
    }

    private static String canonicalTier(String tier, String fallback) {
        if (tier == null || tier.isBlank()) {
            return fallback;
        }

        String normalized = tier.trim().toUpperCase(Locale.ROOT);
        if ("UNASSIGNED".equals(normalized)
            || "PENDING".equals(normalized)
            || "TBD".equals(normalized)
            || "NONE".equals(normalized)) {
            return TIER_NONE;
        }

        return TIER_INDEX.containsKey(normalized) ? normalized : fallback;
    }

    private static int tierIndex(String tier) {
        return TIER_INDEX.getOrDefault(tier, 0);
    }

    private static String stepTier(String tier, int step) {
        int index = tierIndex(tier);
        int nextIndex = Math.max(0, Math.min(ORDERED_TIERS.size() - 1, index + step));
        return ORDERED_TIERS.get(nextIndex);
    }

    private static boolean canPromote(String currentTier, int mmr) {
        String nextTier = stepTier(currentTier, 1);
        if (nextTier.equals(currentTier)) {
            return false;
        }

        int requiredMmr = TIER_FLOOR_MMR.getOrDefault(nextTier, Integer.MAX_VALUE) + PROMOTION_MMR_BUFFER;
        return mmr >= requiredMmr;
    }

    private static boolean canDemote(String currentTier, int mmr) {
        String previousTier = stepTier(currentTier, -1);
        if (previousTier.equals(currentTier)) {
            return false;
        }

        int currentTierFloor = TIER_FLOOR_MMR.getOrDefault(currentTier, 0);
        return mmr < (currentTierFloor - DEMOTION_MMR_BUFFER);
    }
}
