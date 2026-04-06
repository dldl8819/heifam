package com.balancify.backend.api.admin.dto;

public record RatingRecalculationRequest(
    Boolean confirm,
    Boolean dryRun
) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirm);
    }

    public boolean dryRunEnabled() {
        return Boolean.TRUE.equals(dryRun);
    }
}
