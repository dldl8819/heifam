package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PrizeDrawResponse(
    Long id,
    String title,
    // FIRST or LAST.
    String mode,
    int entrantCount,
    OffsetDateTime createdAt,
    // The admin who saved it; null for one without a nickname or who has left.
    String savedByNickname,
    boolean canDelete,
    List<PrizeDrawWinnerResponse> winners
) {
}
