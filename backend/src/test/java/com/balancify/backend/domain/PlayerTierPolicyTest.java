package com.balancify.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerTierPolicyTest {

    @Test
    void resolvesTierByMmrBoundaries() {
        assertThat(PlayerTierPolicy.resolveTier(-10)).isEqualTo("NONE");
        assertThat(PlayerTierPolicy.resolveTier(0)).isEqualTo("NONE");
        assertThat(PlayerTierPolicy.resolveTier(1)).isEqualTo("C-");
        assertThat(PlayerTierPolicy.resolveTier(199)).isEqualTo("C-");
        assertThat(PlayerTierPolicy.resolveTier(399)).isEqualTo("C-");
        assertThat(PlayerTierPolicy.resolveTier(400)).isEqualTo("C");
        assertThat(PlayerTierPolicy.resolveTier(599)).isEqualTo("C");
        assertThat(PlayerTierPolicy.resolveTier(600)).isEqualTo("C+");
        assertThat(PlayerTierPolicy.resolveTier(799)).isEqualTo("C+");
        assertThat(PlayerTierPolicy.resolveTier(800)).isEqualTo("B-");
        assertThat(PlayerTierPolicy.resolveTier(999)).isEqualTo("B-");
        assertThat(PlayerTierPolicy.resolveTier(1000)).isEqualTo("B");
        assertThat(PlayerTierPolicy.resolveTier(1199)).isEqualTo("B");
        assertThat(PlayerTierPolicy.resolveTier(1200)).isEqualTo("B+");
        assertThat(PlayerTierPolicy.resolveTier(1399)).isEqualTo("B+");
        assertThat(PlayerTierPolicy.resolveTier(1400)).isEqualTo("A-");
        assertThat(PlayerTierPolicy.resolveTier(1599)).isEqualTo("A-");
        assertThat(PlayerTierPolicy.resolveTier(1600)).isEqualTo("A");
        assertThat(PlayerTierPolicy.resolveTier(1799)).isEqualTo("A");
        assertThat(PlayerTierPolicy.resolveTier(1800)).isEqualTo("A+");
        assertThat(PlayerTierPolicy.resolveTier(1999)).isEqualTo("A+");
        assertThat(PlayerTierPolicy.resolveTier(2000)).isEqualTo("S");
    }

    @Test
    void detectsLowTierFromMmrIncludingNone() {
        assertThat(PlayerTierPolicy.isLowTier(0)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(1)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(250)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(399)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(799)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(800)).isFalse();
        assertThat(PlayerTierPolicy.isLowTier(1000)).isFalse();
    }

    @Test
    void protectsDemotionDuringPlacement() {
        assertThat(PlayerTierPolicy.resolveTierForRankedMatch("A", 730, 2))
            .isEqualTo("A");
    }

    @Test
    void demotesAfterPlacementWhenMmrFallsBelowShield() {
        assertThat(PlayerTierPolicy.resolveTierForRankedMatch("A", 1540, 6))
            .isEqualTo("A-");
    }

    @Test
    void doesNotDemoteInsideDemotionShieldRange() {
        assertThat(PlayerTierPolicy.resolveTierForRankedMatch("A", 1560, 6))
            .isEqualTo("A");
    }

    @Test
    void requiresPromotionBufferBeforeTierUp() {
        assertThat(PlayerTierPolicy.resolveTierForRankedMatch("B", 1220, 6))
            .isEqualTo("B");
        assertThat(PlayerTierPolicy.resolveTierForRankedMatch("B", 1230, 6))
            .isEqualTo("B+");
    }

    @Test
    void promotesOnlyOneStepPerMatchResult() {
        assertThat(PlayerTierPolicy.resolveTierForRankedMatch("B", 1980, 10))
            .isEqualTo("B+");
    }

    @Test
    void snapshotTierFallsBackToMmrWhenTierIsUnknown() {
        assertThat(PlayerTierPolicy.resolveTierForSnapshot("재배정대상", 1630))
            .isEqualTo("A");
    }

    @Test
    void demotesTierByRequestedSteps() {
        assertThat(PlayerTierPolicy.demoteTier("A+", 1)).isEqualTo("A");
        assertThat(PlayerTierPolicy.demoteTier("A+", 2)).isEqualTo("A-");
        assertThat(PlayerTierPolicy.demoteTier("C-", 1)).isEqualTo("NONE");
    }

    @Test
    void demoteTierNormalizesUnknownTierToNone() {
        assertThat(PlayerTierPolicy.demoteTier("재배정대상", 1)).isEqualTo("NONE");
    }

    @Test
    void resolvesDormancyAdjustedMmrToNearTopOfDemotedTier() {
        assertThat(PlayerTierPolicy.resolveDormancyAdjustedMmr("A+", 1930, 1)).isEqualTo(1790);
        assertThat(PlayerTierPolicy.resolveDormancyAdjustedMmr("A", 1680, 1)).isEqualTo(1590);
    }

    @Test
    void doesNotIncreaseMmrWhenDormancyTargetIsHigherThanCurrent() {
        assertThat(PlayerTierPolicy.resolveDormancyAdjustedMmr("A+", 1750, 1)).isEqualTo(1750);
        assertThat(PlayerTierPolicy.resolveDormancyAdjustedMmr("NONE", 0, 1)).isEqualTo(0);
    }
}
