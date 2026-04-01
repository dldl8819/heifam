package com.balancify.backend.api.match.dto;

public record MatchImportFailedRowResponse(
    int rowIndex,
    String matchCode,
    String reason
) {
}

