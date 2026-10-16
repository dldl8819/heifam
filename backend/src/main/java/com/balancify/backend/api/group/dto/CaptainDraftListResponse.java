package com.balancify.backend.api.group.dto;

import java.util.List;

/** The latest regular drafts, newest first. */
public record CaptainDraftListResponse(
    List<CaptainDraftSummaryResponse> drafts
) {
}
