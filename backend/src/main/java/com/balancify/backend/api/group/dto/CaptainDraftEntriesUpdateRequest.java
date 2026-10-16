package com.balancify.backend.api.group.dto;

import java.util.List;

/** The round compositions and the sets, as the host saves them in one go. */
public record CaptainDraftEntriesUpdateRequest(
    List<CaptainDraftRoundUpdateRequest> rounds,
    List<CaptainDraftEntryUpdateItemRequest> entries
) {
}
