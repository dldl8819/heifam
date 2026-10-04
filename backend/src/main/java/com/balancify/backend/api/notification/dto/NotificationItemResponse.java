package com.balancify.backend.api.notification.dto;

import java.time.OffsetDateTime;

/** kind is NOTICE or PREDICTION; link is a path in the site. */
public record NotificationItemResponse(
    Long id,
    String kind,
    String title,
    String body,
    String link,
    OffsetDateTime createdAt,
    boolean read
) {
}
