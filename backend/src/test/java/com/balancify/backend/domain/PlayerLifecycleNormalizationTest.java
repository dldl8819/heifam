package com.balancify.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerLifecycleNormalizationTest {

    @Test
    void doesNotRestoreWithdrawnLifecycleFromAnInconsistentActiveFlag() {
        Player player = new Player();
        player.setActive(true);
        player.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);

        player.syncTierWithMmr();

        assertThat(player.isActive()).isFalse();
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.WITHDRAWN);
    }

    @Test
    void doesNotRestoreInactiveLifecycleFromAnInconsistentActiveFlag() {
        Player player = new Player();
        player.setActive(true);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);

        player.syncTierWithMmr();

        assertThat(player.isActive()).isFalse();
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.INACTIVE);
    }
}
