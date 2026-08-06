package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
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
    void hidesIdentityWhenActiveFlagConflictsWithNonActiveLifecycle() {
        Player player = new Player();
        player.setId(104L);
        player.setNickname("INCONSISTENT_NICKNAME");
        player.setActive(true);
        player.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);

        assertThat(PlayerIdentityPolicy.isIdentityHidden(player)).isTrue();
        assertThat(PlayerIdentityPolicy.responsePlayerId(player)).isNull();
        assertThat(PlayerIdentityPolicy.responseNickname(player))
            .isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
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
        player.setRetentionSubjectHash("PLACEHOLDER_RETENTION_HASH");
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
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ANONYMIZED);
        assertThat(player.getIdentityRetainedUntil()).isNull();
        assertThat(player.getRetentionSubjectHash()).isNull();
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

    @Test
    void retainsMinimalInactiveIdentityForAdminUntilExpiry() {
        OffsetDateTime inactiveAt = OffsetDateTime.parse("2026-07-19T00:00:00Z");
        OffsetDateTime retainedUntil = inactiveAt.plusYears(1);
        Player player = new Player();
        player.setAuthUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        player.setNickname("RETAINED_NICKNAME");
        player.setRace("T");
        player.setNote("REMOVE_NOTE");

        PlayerIdentityPolicy.retainAdministrativeIdentity(
            player,
            PlayerLifecycleStatus.INACTIVE,
            inactiveAt,
            "운영 정책",
            retainedUntil
        );

        assertThat(player.getAuthUserId()).isNull();
        assertThat(player.getNickname()).isEqualTo("RETAINED_NICKNAME");
        assertThat(player.getRace()).isEqualTo("T");
        assertThat(player.getNote()).isNull();
        assertThat(player.getChatLeftReason()).isEqualTo("운영 정책");
        assertThat(PlayerIdentityPolicy.isIdentityHidden(player)).isTrue();
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(
            player,
            retainedUntil.minusSeconds(1)
        )).isTrue();
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, retainedUntil)).isFalse();
    }

    @Test
    void neverAllowsWithdrawnPlayerReactivation() {
        Player player = new Player();
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> PlayerIdentityPolicy.reactivate(player, OffsetDateTime.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAdministrativeRetentionWhenDirectIdentityOrInvalidLinkageRemains() {
        OffsetDateTime inactiveAt = OffsetDateTime.parse("2026-07-19T00:00:00Z");
        OffsetDateTime now = inactiveAt.plusDays(1);
        Player player = new Player();
        player.setNickname("RETAINED_NICKNAME");
        PlayerIdentityPolicy.retainAdministrativeIdentity(
            player,
            PlayerLifecycleStatus.INACTIVE,
            inactiveAt,
            PlayerIdentityPolicy.ALLOWED_INACTIVE_REASONS.iterator().next(),
            inactiveAt.plusYears(1)
        );

        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isTrue();

        player.setAuthUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();
        player.setAuthUserId(null);

        player.setNote("REMOVE_NOTE");
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();
        player.setNote(null);

        player.setChatRejoinedAt(now);
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();
        player.setChatRejoinedAt(null);

        player.setTierChangeAcknowledgedTier("A");
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();
        player.setTierChangeAcknowledgedTier(null);

        player.setTierChangeAcknowledgedAt(now);
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();
        player.setTierChangeAcknowledgedAt(null);

        player.setRetentionSubjectHash("INVALID_RETENTION_LINK");
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();

        player.setRetentionSubjectHash("a".repeat(64));
        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isTrue();
    }

    @Test
    void neverExposesLegacyWithdrawnIdentityToAdministrators() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-20T00:00:00Z");
        Player player = new Player();
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);
        player.setIdentityRetainedUntil(now.plusYears(5));

        assertThat(PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            PlayerIdentityPolicy.retainAdministrativeIdentity(
                player,
                PlayerLifecycleStatus.WITHDRAWN,
                now,
                "본인 요청",
                now.plusYears(1)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reactivationClearsExpiredPurposeInactiveMetadata() {
        OffsetDateTime rejoinedAt = OffsetDateTime.parse("2026-07-20T00:00:00Z");
        Player player = new Player();
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        player.setChatLeftAt(OffsetDateTime.parse("2026-07-19T00:00:00Z"));
        player.setChatLeftReason("PAST_INACTIVE_REASON");
        player.setIdentityRetainedUntil(rejoinedAt.plusYears(5));
        player.setRetentionSubjectHash("PLACEHOLDER_RETENTION_HASH");

        PlayerIdentityPolicy.reactivate(player, rejoinedAt);

        assertThat(player.isActive()).isTrue();
        assertThat(player.getChatLeftAt()).isNull();
        assertThat(player.getChatLeftReason()).isNull();
        assertThat(player.getChatRejoinedAt()).isEqualTo(rejoinedAt);
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ACTIVE);
        assertThat(player.getIdentityRetainedUntil()).isNull();
        assertThat(player.getRetentionSubjectHash()).isNull();
    }
}
