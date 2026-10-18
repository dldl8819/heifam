package com.balancify.backend.api.group.dto;

import java.util.List;

/**
 * A new regular draft. setSizes is the team size of each set of a round (3, 2 or 1, in play order);
 * left out, setsPerRound picks that many sets of 3:3 then 2:2, and left out too, enough sets for
 * everyone to play. roundCount defaults to four rounds. entryMode is HOST (the host fills both
 * teams in; the default) or CAPTAINS (each captain hands their own team in, unseen by the other).
 */
public record CaptainDraftCreateRequest(
    String title,
    List<Long> participantPlayerIds,
    List<Long> captainPlayerIds,
    Integer setsPerRound,
    Integer roundCount,
    List<Integer> setSizes,
    String entryMode
) {
}
