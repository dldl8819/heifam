package com.balancify.backend.api.group.dto;

import java.util.List;

public record NoticeVoteOptionResponse(
    Long id,
    String label,
    long count,
    // The reader's own vote is on this option.
    boolean mine,
    // Nicknames of those who chose it, in the order they voted; an entry is null for a voter
    // without a nickname. Null altogether when the vote is anonymous.
    List<String> voters
) {
}
