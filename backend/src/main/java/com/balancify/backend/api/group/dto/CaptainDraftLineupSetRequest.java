package com.balancify.backend.api.group.dto;

import java.util.List;

/** One set of a captain's line-up: the captain's team's players in it, slot by slot, every slot filled. */
public record CaptainDraftLineupSetRequest(
    Integer roundNumber,
    Integer setNumber,
    List<Long> playerIds
) {
}
