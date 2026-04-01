package com.balancify.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerTierPolicyTest {

    @Test
    void resolvesTierByMmrBoundaries() {
        assertThat(PlayerTierPolicy.resolveTier(-10)).isEqualTo("C-");
        assertThat(PlayerTierPolicy.resolveTier(0)).isEqualTo("C-");
        assertThat(PlayerTierPolicy.resolveTier(199)).isEqualTo("C-");
        assertThat(PlayerTierPolicy.resolveTier(200)).isEqualTo("C");
        assertThat(PlayerTierPolicy.resolveTier(299)).isEqualTo("C");
        assertThat(PlayerTierPolicy.resolveTier(300)).isEqualTo("C+");
        assertThat(PlayerTierPolicy.resolveTier(399)).isEqualTo("C+");
        assertThat(PlayerTierPolicy.resolveTier(400)).isEqualTo("B-");
        assertThat(PlayerTierPolicy.resolveTier(499)).isEqualTo("B-");
        assertThat(PlayerTierPolicy.resolveTier(500)).isEqualTo("B");
        assertThat(PlayerTierPolicy.resolveTier(599)).isEqualTo("B");
        assertThat(PlayerTierPolicy.resolveTier(600)).isEqualTo("B+");
        assertThat(PlayerTierPolicy.resolveTier(699)).isEqualTo("B+");
        assertThat(PlayerTierPolicy.resolveTier(700)).isEqualTo("A-");
        assertThat(PlayerTierPolicy.resolveTier(799)).isEqualTo("A-");
        assertThat(PlayerTierPolicy.resolveTier(800)).isEqualTo("A");
        assertThat(PlayerTierPolicy.resolveTier(899)).isEqualTo("A");
        assertThat(PlayerTierPolicy.resolveTier(900)).isEqualTo("A+");
        assertThat(PlayerTierPolicy.resolveTier(999)).isEqualTo("A+");
        assertThat(PlayerTierPolicy.resolveTier(1000)).isEqualTo("S");
        assertThat(PlayerTierPolicy.resolveTier(1400)).isEqualTo("S");
    }

    @Test
    void detectsLowTierFromMmr() {
        assertThat(PlayerTierPolicy.isLowTier(0)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(250)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(399)).isTrue();
        assertThat(PlayerTierPolicy.isLowTier(400)).isFalse();
        assertThat(PlayerTierPolicy.isLowTier(1000)).isFalse();
    }
}
