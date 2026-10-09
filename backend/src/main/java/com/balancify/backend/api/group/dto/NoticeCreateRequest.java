package com.balancify.backend.api.group.dto;

import java.util.List;

public record NoticeCreateRequest(
    String title,
    String content,
    Boolean adminOnly,
    // OPEN asks members to vote on the notice; left out, it has no vote.
    String voteStatus,
    // What can be voted for, in order. Left out, the vote is 찬성 or 반대.
    List<String> voteOptions,
    // Left out, the vote is anonymous: counts are shown, who chose what is not.
    Boolean voteAnonymous,
    // Voters may add options of their own. Left out, they may not.
    Boolean voteAllowAdditions
) {
}
