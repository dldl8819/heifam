package com.balancify.backend.api.group.dto;

import java.time.OffsetDateTime;

/** What visitors who are not members see of a notice: its title and date, nothing more. */
public record NoticeTitleResponse(
    String title,
    OffsetDateTime createdAt
) {
}
