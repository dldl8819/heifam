package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NoticeUpdateRequest(
    String title,
    String content,
    Boolean adminOnly,
    // Announce the edit again: members see the notice unread and are notified of it. Sent as
    // "notify", which a record component cannot be called.
    @JsonProperty("notify") Boolean announceAgain
) {
}
