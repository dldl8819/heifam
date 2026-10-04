package com.balancify.backend.api.series.dto;

import java.util.List;

public record BalanceSeriesListResponse(List<BalanceSeriesResponse> series) {
}
