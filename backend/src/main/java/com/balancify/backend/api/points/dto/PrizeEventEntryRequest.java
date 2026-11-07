package com.balancify.backend.api.points.dto;

/** A player's number in an event ranked by numbers counted outside the site (MANUAL). */
public record PrizeEventEntryRequest(
    Long playerId,
    Long score
) {
}
