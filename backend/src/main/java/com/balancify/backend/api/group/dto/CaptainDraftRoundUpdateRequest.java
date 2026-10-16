package com.balancify.backend.api.group.dto;

/** The race composition a round is played with (PPP, PPT, PPZ or PTZ). */
public record CaptainDraftRoundUpdateRequest(
    Integer roundNumber,
    String roundCode
) {
}
