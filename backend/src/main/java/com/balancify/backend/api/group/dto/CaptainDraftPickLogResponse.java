package com.balancify.backend.api.group.dto;

public record CaptainDraftPickLogResponse(
    int pickOrder,
    Long captainPlayerId,
    String captainNickname,
    Long pickedPlayerId,
    String pickedPlayerNickname,
    String team
) {
}
