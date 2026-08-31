package com.balancify.backend.api.group.dto;

import java.util.List;

public record LedgerCategoriesResponse(
    List<String> categories
) {
}
