package com.balancify.backend.api.access.dto;

public record AccessMeResponse(
    String email,
    String nickname,
    String role,
    boolean admin,
    boolean superAdmin,
    boolean allowed,
    boolean canViewMmr,
    String preferredRace
) {
}
