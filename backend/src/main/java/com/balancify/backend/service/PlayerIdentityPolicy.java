package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.regex.Pattern;

public final class PlayerIdentityPolicy {

    public static final String HIDDEN_MEMBER_LABEL = "\uD0C8\uD1F4\uD55C \uD68C\uC6D0";
    private static final Pattern RETENTION_SUBJECT_HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    public static final Set<String> ALLOWED_INACTIVE_REASONS = Set.of(
        "장기 미참여",
        "본인 요청",
        "운영 정책",
        "기타"
    );

    private PlayerIdentityPolicy() {
    }

    public static boolean isIdentityHidden(Player player) {
        return player == null
            || !player.isActive()
            || player.isAnonymized()
            || player.getLifecycleStatus() != PlayerLifecycleStatus.ACTIVE;
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

    public static boolean isAdministrativeIdentityRetained(Player player, OffsetDateTime now) {
        if (player == null || player.isActive() || player.isAnonymized()) {
            return false;
        }
        PlayerLifecycleStatus status = player.getLifecycleStatus();
        if (status != PlayerLifecycleStatus.INACTIVE) {
            return false;
        }
        OffsetDateTime inactiveAt = player.getChatLeftAt();
        OffsetDateTime retainedUntil = player.getIdentityRetainedUntil();
        if (inactiveAt == null
            || retainedUntil == null
            || now == null
            || inactiveAt.isAfter(now)
            || !retainedUntil.isAfter(now)
            || retainedUntil.isAfter(inactiveAt.plusYears(1))
            || retainedUntil.isAfter(now.plusYears(1))) {
            return false;
        }
        String retentionSubjectHash = player.getRetentionSubjectHash();
        return player.getAuthUserId() == null
            && player.getNote() == null
            && player.getChatRejoinedAt() == null
            && player.getTierChangeAcknowledgedTier() == null
            && player.getTierChangeAcknowledgedAt() == null
            && (retentionSubjectHash == null
                || RETENTION_SUBJECT_HASH_PATTERN.matcher(retentionSubjectHash).matches())
            && isAllowedInactiveReason(player.getChatLeftReason())
            && player.getNickname() != null
            && !player.getNickname().isBlank()
            && !HIDDEN_MEMBER_LABEL.equals(player.getNickname());
    }

    public static void retainAdministrativeIdentity(
        Player player,
        PlayerLifecycleStatus status,
        OffsetDateTime lifecycleAt,
        String reason,
        OffsetDateTime retainedUntil
    ) {
        if (player == null) {
            return;
        }
        if (status != PlayerLifecycleStatus.INACTIVE) {
            throw new IllegalArgumentException("Retained player lifecycle status is invalid");
        }
        if (!isAllowedInactiveReason(reason)) {
            throw new IllegalArgumentException("Inactive reason must use an allowed category");
        }
        player.setAuthUserId(null);
        player.setNote(null);
        player.setActive(false);
        player.setChatLeftAt(lifecycleAt);
        player.setChatLeftReason(reason);
        player.setChatRejoinedAt(null);
        player.setTierChangeAcknowledgedTier(null);
        player.setTierChangeAcknowledgedAt(null);
        player.setAnonymizedAt(null);
        player.setLifecycleStatus(status);
        player.setIdentityRetainedUntil(retainedUntil);
    }

    public static boolean isAllowedInactiveReason(String reason) {
        return reason != null && ALLOWED_INACTIVE_REASONS.contains(reason.trim());
    }

    public static void reactivate(Player player, OffsetDateTime rejoinedAt) {
        if (player == null || player.isAnonymized()
            || player.getLifecycleStatus() == PlayerLifecycleStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Player cannot be reactivated");
        }
        player.setActive(true);
        player.setChatLeftAt(null);
        player.setChatLeftReason(null);
        player.setChatRejoinedAt(rejoinedAt);
        player.setLifecycleStatus(PlayerLifecycleStatus.ACTIVE);
        player.setIdentityRetainedUntil(null);
        player.setRetentionSubjectHash(null);
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
        player.setLifecycleStatus(PlayerLifecycleStatus.ANONYMIZED);
        player.setIdentityRetainedUntil(null);
        player.setRetentionSubjectHash(null);
    }
}
