package com.balancify.backend.api.group.dto;

import java.util.List;

public record BoardSearchResponse(
    // The word searched for, trimmed.
    String query,
    List<BoardSearchItemResponse> results,
    long total,
    int page,
    int pageSize
) {
}
