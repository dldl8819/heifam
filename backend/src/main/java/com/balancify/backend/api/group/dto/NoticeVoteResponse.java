package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * The vote on a notice as a reader sees it: its options with how many chose each, and the
 * reader's own choice. An anonymous vote tells nobody who chose what; a named one lists the
 * voters under each option.
 */
public record NoticeVoteResponse(
    // OPEN or CLOSED; a notice without a vote has no vote object at all.
    String status,
    // The same vote as a page loaded before votes had options reads it: the counts of 찬성 and
    // 반대 (0 when the vote has no such option) and AGREE, DISAGREE or null for the reader's choice.
    long agreeCount,
    long disagreeCount,
    String myChoice,
    boolean anonymous,
    // Voters may add options of their own while the vote is open.
    boolean allowAdditions,
    // This reader may add an option now: the vote is open, has room, and they are an admin or
    // the vote allows additions.
    boolean canAddOption,
    // This reader may remove an option now: an admin, while the vote is open and has more than two.
    boolean canRemoveOptions,
    long totalVoters,
    // Null when the reader has not voted.
    Long myOptionId,
    List<NoticeVoteOptionResponse> options
) {
}
