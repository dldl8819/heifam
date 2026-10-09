package com.balancify.backend.api.group.dto;

import java.util.List;

public record BoardPostListResponse(
    List<BoardPostListItemResponse> posts,
    long total,
    int page,
    int pageSize
) {
}
