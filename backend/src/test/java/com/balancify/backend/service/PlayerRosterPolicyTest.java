package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PlayerRosterPolicyTest {

    private static final OffsetDateTime SET_ASIDE_AT = OffsetDateTime.parse("2026-10-01T03:00:00Z");

    @Test
    void keepsAVisiblePlayerOnTheRosterUntilTheyAreSetAside() {
        Player player = player();
        assertThat(PlayerRosterPolicy.isOnRoster(player)).isTrue();
        assertThat(PlayerRosterPolicy.isDormant(player)).isFalse();

        player.setDormantAt(SET_ASIDE_AT);
        assertThat(PlayerRosterPolicy.isOnRoster(player)).isFalse();
        assertThat(PlayerRosterPolicy.isDormant(player)).isTrue();
        // A dormant player is not hidden: their nickname stays on what they played.
        assertThat(PlayerIdentityPolicy.isIdentityHidden(player)).isFalse();
        assertThat(PlayerIdentityPolicy.responseNickname(player)).isEqualTo("PlayerAlpha");
    }

    @Test
    void countsAHiddenPlayerAsNeitherOnTheRosterNorDormant() {
        Player inactive = player();
        inactive.setActive(false);
        inactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        inactive.setDormantAt(SET_ASIDE_AT);

        assertThat(PlayerRosterPolicy.isOnRoster(inactive)).isFalse();
        assertThat(PlayerRosterPolicy.isDormant(inactive)).isFalse();
        assertThat(PlayerRosterPolicy.isOnRoster(null)).isFalse();
        assertThat(PlayerRosterPolicy.isDormant(null)).isFalse();
    }

    private Player player() {
        Player player = new Player();
        player.setId(1L);
        player.setNickname("PlayerAlpha");
        player.setMmr(1000);
        return player;
    }
}
