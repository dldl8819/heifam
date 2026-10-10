package com.balancify.backend.api.group.dto;

import java.util.List;

public record PrizeDrawListResponse(
    // Newest first.
    List<PrizeDrawResponse> draws,
    // The reader may run a draw and save its outcome.
    boolean canRun
) {
}
