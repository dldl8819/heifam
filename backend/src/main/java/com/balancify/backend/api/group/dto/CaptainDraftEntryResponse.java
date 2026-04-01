package com.balancify.backend.api.group.dto;

public record CaptainDraftEntryResponse(
    int roundNumber,
    String roundCode,
    int setNumber,
    Long homePlayerId,
    String homePlayerNickname,
    Long awayPlayerId,
    String awayPlayerNickname
) {
}
