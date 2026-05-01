package com.balancify.backend.api.access.dto;

public record AccessEmailEntryResponse(
    String email,
    String nickname,
    boolean canViewMmr
) {
}
