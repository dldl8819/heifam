package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import java.time.OffsetDateTime;

public final class PlayerIdentityPolicy {

    public static final String HIDDEN_MEMBER_LABEL = "\uD0C8\uD1F4\uD55C \uD68C\uC6D0";

    private PlayerIdentityPolicy() {
    }

    public static boolean isIdentityHidden(Player player) {
        return player == null || !player.isActive() || player.isAnonymized();
    }

    public static Long responsePlayerId(Player player) {
        return isIdentityHidden(player) ? null : player.getId();
    }

    public static String responseNickname(Player player) {
        if (player == null) {
            return null;
        }
        return isIdentityHidden(player) ? HIDDEN_MEMBER_LABEL : player.getNickname();
    }

    public static void anonymize(Player player, OffsetDateTime anonymizedAt) {
        if (player == null) {
            return;
        }
        player.setAuthUserId(null);
        player.setNickname(HIDDEN_MEMBER_LABEL);
        player.setNote(null);
        player.setActive(false);
        player.setChatLeftAt(null);
        player.setChatLeftReason(null);
        player.setChatRejoinedAt(null);
        player.setTierChangeAcknowledgedTier(null);
        player.setTierChangeAcknowledgedAt(null);
        player.setAnonymizedAt(anonymizedAt == null ? OffsetDateTime.now() : anonymizedAt);
    }
}
