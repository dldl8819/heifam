package com.balancify.backend.api.access.dto;

public record AccessEmailUpsertRequest(
    String email,
    String nickname
) {
}
