package com.balancify.backend.api.group.dto;

import java.util.List;

public record GroupMatchPageResponse(
    List<GroupRecentMatchResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}
