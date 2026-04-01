package com.balancify.backend.api.group.dto;

public record CaptainDraftPickRequest(
    Long captainPlayerId,
    Long pickedPlayerId
) {
}
