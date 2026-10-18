package com.balancify.backend.api.group.dto;

/** An ace decider for a draft whose regular rounds ended level: one set of teamSize a side (1 if left out). */
public record CaptainDraftAceRequest(
    Integer teamSize
) {
}
