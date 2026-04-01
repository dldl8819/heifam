package com.balancify.backend.api.match.dto;

import java.util.List;

public record MatchImportRowRequest(
    Long groupId,
    String matchCode,
    String playedAt,
    List<String> homeTeam,
    List<String> awayTeam,
    String winnerTeam
) {
}

