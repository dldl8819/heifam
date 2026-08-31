package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupPlayerResponse(
    Long id,
    String nickname,
    String race,
    String tier,
    Integer baseMmr,
    String baseTier,
    Integer currentMmr,
    OffsetDateTime lastTierSnapshotAt,
    Integer lastTierSnapshotMmr,
    String lastTierSnapshotTier,
    String liveTier,
    int wins,
    int losses,
    int games,
    boolean active,
    OffsetDateTime chatLeftAt,
    String chatLeftReason,
    OffsetDateTime chatRejoinedAt,
    String tierChangeAcknowledgedTier,
    OffsetDateTime tierChangeAcknowledgedAt,
    String lifecycleStatus,
    OffsetDateTime identityRetainedUntil,
    boolean isOwnPlayer
) {
    public GroupPlayerResponse(
        Long id,
        String nickname,
        String race,
        String tier,
        Integer baseMmr,
        String baseTier,
        Integer currentMmr,
        OffsetDateTime lastTierSnapshotAt,
        Integer lastTierSnapshotMmr,
        String lastTierSnapshotTier,
        String liveTier,
        int wins,
        int losses,
        int games,
        boolean active,
        OffsetDateTime chatLeftAt,
        String chatLeftReason,
        OffsetDateTime chatRejoinedAt,
        String tierChangeAcknowledgedTier,
        OffsetDateTime tierChangeAcknowledgedAt,
        boolean isOwnPlayer
    ) {
        this(
            id,
            nickname,
            race,
            tier,
            baseMmr,
            baseTier,
            currentMmr,
            lastTierSnapshotAt,
            lastTierSnapshotMmr,
            lastTierSnapshotTier,
            liveTier,
            wins,
            losses,
            games,
            active,
            chatLeftAt,
            chatLeftReason,
            chatRejoinedAt,
            tierChangeAcknowledgedTier,
            tierChangeAcknowledgedAt,
            null,
            null,
            isOwnPlayer
        );
    }
}
