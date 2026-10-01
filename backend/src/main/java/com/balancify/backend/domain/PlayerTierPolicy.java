package com.balancify.backend.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerTierPolicy {

    public static final int MIN_EDITABLE_MMR = -2000;
    public static final int MAX_EDITABLE_MMR = 5000;

    private static final String TIER_NONE = "NONE";
    private static final List<String> ORDERED_TIERS = List.of(
        TIER_NONE, "D-", "D", "D+", "C-", "C", "C+", "B-", "B", "B+", "A-", "A", "A+", "S-", "S", "S+"
    );
    private static final Map<String, Integer> TIER_INDEX = Map.ofEntries(
        Map.entry(TIER_NONE, 0),
        Map.entry("D-", 1),
        Map.entry("D", 2),
        Map.entry("D+", 3),
        Map.entry("C-", 4),
        Map.entry("C", 5),
        Map.entry("C+", 6),
        Map.entry("B-", 7),
        Map.entry("B", 8),
        Map.entry("B+", 9),
        Map.entry("A-", 10),
        Map.entry("A", 11),
        Map.entry("A+", 12),
        Map.entry("S-", 13),
        Map.entry("S", 14),
        Map.entry("S+", 15)
    );
    // D- has no lower bound; its entry is only the MMR a player is given when assigned D-.
    private static final Map<String, Integer> TIER_FLOOR_MMR = Map.ofEntries(
        Map.entry(TIER_NONE, 0),
        Map.entry("D-", -400),
        Map.entry("D", -200),
        Map.entry("D+", 0),
        Map.entry("C-", 200),
        Map.entry("C", 400),
        Map.entry("C+", 600),
        Map.entry("B-", 800),
        Map.entry("B", 1000),
        Map.entry("B+", 1200),
        Map.entry("A-", 1400),
        Map.entry("A", 1600),
        Map.entry("A+", 1800),
        Map.entry("S-", 2000),
        Map.entry("S", 2200),
        Map.entry("S+", 2400)
    );
    private static final int PROMOTION_MMR_BUFFER = 30;
    private static final int DEMOTION_MMR_BUFFER = 50;

    private PlayerTierPolicy() {
    }

    public static String resolveTier(Integer mmr) {
        int normalizedMmr = mmr == null ? 0 : mmr;
        if (normalizedMmr < -200) {
            return "D-";
        }
        if (normalizedMmr < 0) {
            return "D";
        }
        if (normalizedMmr < 200) {
            return "D+";
        }
        if (normalizedMmr < 400) {
            return "C-";
        }
        if (normalizedMmr < 600) {
            return "C";
        }
        if (normalizedMmr < 800) {
            return "C+";
        }
        if (normalizedMmr < 1000) {
            return "B-";
        }
        if (normalizedMmr < 1200) {
            return "B";
        }
        if (normalizedMmr < 1400) {
            return "B+";
        }
        if (normalizedMmr < 1600) {
            return "A-";
        }
        if (normalizedMmr < 1800) {
            return "A";
        }
        if (normalizedMmr < 2000) {
            return "A+";
        }
        if (normalizedMmr < 2200) {
            return "S-";
        }
        if (normalizedMmr < 2400) {
            return "S";
        }
        return "S+";
    }

    // A player is unassigned until a tier is set for them; their next rated match places them by score.
    public static String resolveLiveTier(String storedTier, Integer mmr) {
        return isUnassigned(storedTier) ? TIER_NONE : resolveTier(mmr);
    }

    public static boolean isUnassigned(String storedTier) {
        return TIER_NONE.equals(canonicalTier(storedTier, TIER_NONE));
    }

    public static String resolveTierForRankedMatch(String currentTier, Integer mmr) {
        int normalizedMmr = mmr == null ? 0 : mmr;
        String targetTier = resolveTier(normalizedMmr);
        String normalizedCurrentTier = canonicalTier(currentTier, targetTier);
        if (TIER_NONE.equals(normalizedCurrentTier)) {
            return targetTier;
        }

        int currentTierIndex = tierIndex(normalizedCurrentTier);
        int targetTierIndex = tierIndex(targetTier);

        if (targetTierIndex > currentTierIndex && canPromote(normalizedCurrentTier, normalizedMmr)) {
            return stepTier(normalizedCurrentTier, 1);
        }

        if (targetTierIndex < currentTierIndex && canDemote(normalizedCurrentTier, normalizedMmr)) {
            return stepTier(normalizedCurrentTier, -1);
        }

        return normalizedCurrentTier;
    }

    public static String resolveTierForSnapshot(String tier) {
        return canonicalTier(tier, TIER_NONE);
    }

    public static int resolveDefaultMmrForTier(String tier) {
        String normalizedTier = canonicalTier(tier, TIER_NONE);
        return TIER_FLOOR_MMR.getOrDefault(normalizedTier, 0);
    }

    public static String normalizeRankedTier(String tier) {
        String normalizedTier = canonicalTier(tier, "");
        return TIER_NONE.equals(normalizedTier) ? "" : normalizedTier;
    }

    public static String resolveHigherRankedTier(String firstTier, String secondTier) {
        String normalizedFirstTier = normalizeRankedTier(firstTier);
        String normalizedSecondTier = normalizeRankedTier(secondTier);
        if (normalizedFirstTier.isEmpty()) {
            return normalizedSecondTier;
        }
        if (normalizedSecondTier.isEmpty()) {
            return normalizedFirstTier;
        }

        return tierIndex(normalizedFirstTier) >= tierIndex(normalizedSecondTier)
            ? normalizedFirstTier
            : normalizedSecondTier;
    }

    public static String demoteTier(String tier, int steps) {
        if (steps <= 0) {
            return canonicalTier(tier, TIER_NONE);
        }

        String normalizedTier = canonicalTier(tier, TIER_NONE);
        return stepTier(normalizedTier, -steps);
    }

    public static boolean isLowTier(Integer mmr) {
        return tierIndex(resolveTier(mmr)) <= tierIndex("C+");
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
        int lowerBound = TIER_NONE.equals(tier) ? 0 : 1;
        int nextIndex = Math.max(lowerBound, Math.min(ORDERED_TIERS.size() - 1, index + step));
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
