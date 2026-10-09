package com.balancify.backend.api.group.dto;

/**
 * The vote on a notice as a reader sees it: how many are for and against, and their own choice.
 * Who chose what is told to nobody.
 */
public record NoticeVoteResponse(
    // OPEN or CLOSED; a notice without a vote has no vote object at all.
    String status,
    long agreeCount,
    long disagreeCount,
    // AGREE, DISAGREE, or null when the reader has not voted.
    String myChoice
) {
}
