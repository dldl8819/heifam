package com.balancify.backend.api.match.dto;

public record BalancePlayerDto(
    Long playerId,
    String name,
    int mmr
) {
    public BalancePlayerDto(String name, int mmr) {
        this(null, name, mmr);
    }
}
