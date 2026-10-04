package com.balancify.backend.api.points.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Candidates are listed while the event is open; winners once it is confirmed. */
public record PrizeEventResponse(
    Long eventId,
    String title,
    LocalDate periodStart,
    LocalDate periodEnd,
    int winnerCount,
    String status,
    OffsetDateTime confirmedAt,
    List<PrizeCandidateResponse> candidates,
    List<PrizeWinnerResponse> winners
) {
}
