package com.balancify.backend.api.points.dto;

import java.util.List;

public record PrizeEventListResponse(
    List<PrizeEventResponse> events
) {
}
