package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * A vote that was taken off its notice, as it is kept: putting the vote back brings these options
 * and votes back. Only admins are sent it, for the edit form.
 */
public record NoticeVoteKeptResponse(
    List<String> options,
    long totalVoters,
    boolean anonymous,
    boolean allowAdditions
) {
}
