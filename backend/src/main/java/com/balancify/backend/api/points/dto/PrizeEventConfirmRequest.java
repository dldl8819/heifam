package com.balancify.backend.api.points.dto;

import java.time.LocalDate;
import java.util.List;

public record PrizeEventConfirmRequest(
    LocalDate paidOn,
    List<PrizeWinnerRequest> winners
) {
}
