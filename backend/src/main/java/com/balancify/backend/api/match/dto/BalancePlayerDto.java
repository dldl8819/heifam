package com.balancify.backend.api.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalancePlayerDto(
    Long playerId,
    String name,
    Integer mmr
) {
    public BalancePlayerDto(String name, int mmr) {
        this(null, name, mmr);
    }
}
