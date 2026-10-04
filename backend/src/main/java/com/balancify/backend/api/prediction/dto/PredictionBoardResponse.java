package com.balancify.backend.api.prediction.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** now is the server clock, so the page can count down to closesAt without trusting its own. */
public record PredictionBoardResponse(
    OffsetDateTime now,
    int windowMinutes,
    List<PredictionMatchResponse> open,
    List<PredictionMatchResponse> closed,
    List<PredictionMatchResponse> history,
    PredictionStatsResponse stats
) {
}
