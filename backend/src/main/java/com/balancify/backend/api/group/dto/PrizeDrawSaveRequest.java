package com.balancify.backend.api.group.dto;

import java.util.List;

public record PrizeDrawSaveRequest(
    String title,
    // FIRST: the first balls to arrive won. LAST: the last ones did.
    String mode,
    // How many balls ran.
    Integer entrantCount,
    List<PrizeDrawWinnerRequest> winners
) {
}
