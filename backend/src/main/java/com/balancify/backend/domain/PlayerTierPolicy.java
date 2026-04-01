package com.balancify.backend.domain;

public final class PlayerTierPolicy {

    private PlayerTierPolicy() {
    }

    public static String resolveTier(Integer mmr) {
        int normalizedMmr = mmr == null ? 0 : mmr;

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

    public static boolean isLowTier(Integer mmr) {
        String tier = resolveTier(mmr);
        return "C+".equals(tier) || "C".equals(tier) || "C-".equals(tier);
    }
}
