package com.balancify.backend.api.match.dto;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum MultiBalanceMode {
    MMR_FIRST,
    DIVERSITY_FIRST,
    RACE_DISTRIBUTION_FIRST;

    public static MultiBalanceMode fromNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return MMR_FIRST;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (MultiBalanceMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }

        throw new IllegalArgumentException(
            "Invalid balanceMode. Supported values: " +
            Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))
        );
    }
}
