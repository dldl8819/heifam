package com.balancify.backend.api.group.dto;

import java.util.List;

/** The sets of every round, as the host saves them in one go. */
public record CaptainDraftEntriesUpdateRequest(
    List<CaptainDraftEntryUpdateItemRequest> entries
) {
}
