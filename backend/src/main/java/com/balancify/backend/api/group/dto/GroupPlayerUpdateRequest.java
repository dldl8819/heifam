package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

public record GroupPlayerUpdateRequest(
    String nickname,
    String race,
    String tier,
    Boolean active,
    OffsetDateTime chatLeftAt,
    String chatLeftReason,
    OffsetDateTime chatRejoinedAt,
    String tierChangeAcknowledgedTier,
    String dormancyMmrFloorTier
) {
    public GroupPlayerUpdateRequest(
        String nickname,
        String race,
        String tier,
        Boolean active,
        OffsetDateTime chatLeftAt,
        String chatLeftReason,
        OffsetDateTime chatRejoinedAt,
        String tierChangeAcknowledgedTier
    ) {
        this(
            nickname,
            race,
            tier,
            active,
            chatLeftAt,
            chatLeftReason,
            chatRejoinedAt,
            tierChangeAcknowledgedTier,
            null
        );
    }

    public GroupPlayerUpdateRequest(
        String nickname,
        String race,
        Boolean active,
        OffsetDateTime chatLeftAt,
        String chatLeftReason,
        OffsetDateTime chatRejoinedAt,
        String tierChangeAcknowledgedTier
    ) {
        this(nickname, race, null, active, chatLeftAt, chatLeftReason, chatRejoinedAt, tierChangeAcknowledgedTier, null);
    }
}
