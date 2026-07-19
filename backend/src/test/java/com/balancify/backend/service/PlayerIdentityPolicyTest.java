package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.domain.Player;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerIdentityPolicyTest {

    @Test
    void masksLegacyInactivePlayerEvenWithoutAnonymizedTimestamp() {
        Player player = new Player();
        player.setId(101L);
        player.setNickname("LEGACY_NICKNAME");
        player.setActive(false);

        assertThat(player.getAnonymizedAt()).isNull();
        assertThat(PlayerIdentityPolicy.isIdentityHidden(player)).isTrue();
        assertThat(PlayerIdentityPolicy.responsePlayerId(player)).isNull();
        assertThat(PlayerIdentityPolicy.responseNickname(player))
            .isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
    }

    @Test
    void keepsActivePlayerResponseIdentityUnchanged() {
        Player player = new Player();
        player.setId(102L);
        player.setNickname("ACTIVE_NICKNAME");
        player.setActive(true);

        assertThat(PlayerIdentityPolicy.isIdentityHidden(player)).isFalse();
        assertThat(PlayerIdentityPolicy.responsePlayerId(player)).isEqualTo(102L);
        assertThat(PlayerIdentityPolicy.responseNickname(player)).isEqualTo("ACTIVE_NICKNAME");
    }

    @Test
    void anonymizeClearsDirectIdentityFieldsButPreservesRatingState() {
        OffsetDateTime anonymizedAt = OffsetDateTime.parse("2026-07-19T00:00:00Z");
        Player player = new Player();
        player.setId(103L);
        player.setAuthUserId(UUID.randomUUID());
        player.setNickname("PROFILE_NICKNAME");
        player.setNote("PROFILE_NOTE");
        player.setActive(true);
        player.setChatLeftAt(OffsetDateTime.parse("2026-07-18T00:00:00Z"));
        player.setChatLeftReason("CHAT_REASON");
        player.setChatRejoinedAt(OffsetDateTime.parse("2026-07-18T01:00:00Z"));
        player.setTierChangeAcknowledgedTier("A");
        player.setTierChangeAcknowledgedAt(OffsetDateTime.parse("2026-07-18T02:00:00Z"));
        player.setMmr(1200);
        player.setRace("P");
        player.setTier("A");

        PlayerIdentityPolicy.anonymize(player, anonymizedAt);

        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(player.getNote()).isNull();
        assertThat(player.isActive()).isFalse();
        assertThat(player.getChatLeftAt()).isNull();
        assertThat(player.getChatLeftReason()).isNull();
        assertThat(player.getChatRejoinedAt()).isNull();
        assertThat(player.getTierChangeAcknowledgedTier()).isNull();
        assertThat(player.getTierChangeAcknowledgedAt()).isNull();
        assertThat(player.getAnonymizedAt()).isEqualTo(anonymizedAt);
        assertThat(player.getMmr()).isEqualTo(1200);
        assertThat(player.getRace()).isEqualTo("P");
        assertThat(player.getTier()).isEqualTo("A");
    }

    @Test
    void treatsMissingPlayerAsHiddenWithoutReturningIdentity() {
        assertThat(PlayerIdentityPolicy.isIdentityHidden(null)).isTrue();
        assertThat(PlayerIdentityPolicy.responsePlayerId(null)).isNull();
        assertThat(PlayerIdentityPolicy.responseNickname(null)).isNull();
    }
}
