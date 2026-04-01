package com.balancify.backend.api.group.dto;

public record GroupPlayerImportRowRequest(
    String nickname,
    String tier,
    Integer baseMmr,
    Integer currentMmr,
    String note,
    String race
) {
    public GroupPlayerImportRowRequest(
        String nickname,
        String tier,
        Integer baseMmr,
        Integer currentMmr,
        String note
    ) {
        this(nickname, tier, baseMmr, currentMmr, note, null);
    }
}
