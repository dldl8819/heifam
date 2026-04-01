package com.balancify.backend.api.group.dto;

import java.util.List;

public record CaptainDraftEntriesUpdateRequest(
    Long captainPlayerId,
    List<CaptainDraftEntryUpdateItemRequest> entries
) {
}
