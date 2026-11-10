package com.balancify.backend.api.group.dto;

import java.util.List;

/** A page of the regular drafts, newest first, and whether older ones follow it. */
public record CaptainDraftListResponse(
    List<CaptainDraftSummaryResponse> drafts,
    boolean hasMore
) {
}
