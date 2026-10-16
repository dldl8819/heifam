package com.balancify.backend.api.group.dto;

import java.util.List;

/** The round entries; without a captain, the host sets both teams at once. */
public record CaptainDraftEntriesUpdateRequest(
    Long captainPlayerId,
    List<CaptainDraftEntryUpdateItemRequest> entries
) {
}
