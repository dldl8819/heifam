package com.balancify.backend.api.group.dto;

public record GroupPlayerImportFailedRowResponse(
    String nickname,
    String reason
) {
}
