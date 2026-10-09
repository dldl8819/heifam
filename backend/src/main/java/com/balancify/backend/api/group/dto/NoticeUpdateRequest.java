package com.balancify.backend.api.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NoticeUpdateRequest(
    String title,
    String content,
    Boolean adminOnly,
    // Announce the edit again: members see the notice unread and are notified of it. Sent as
    // "notify", which a record component cannot be called.
    @JsonProperty("notify") Boolean announceAgain,
    // NONE, OPEN or CLOSED; left out, the vote stays as it is.
    String voteStatus,
    // The options in order; left out, they stay as they are. They can be changed only while
    // nobody has voted.
    List<String> voteOptions,
    // Left out, it stays as it is. An anonymous vote that has votes cannot be made a named one.
    Boolean voteAnonymous,
    // Left out, it stays as it is.
    Boolean voteAllowAdditions
) {
}
