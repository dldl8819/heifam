package com.balancify.backend.repository;

/**
 * Where a notification list stands: how many notifications its audiences hold and the newest one's
 * id. A notification is never edited, only added (a higher id) or removed (one fewer), so the two
 * move whenever the list does, and they are one row to read instead of the list.
 */
public record NotificationListMark(
    Long count,
    Long newestId
) {
}
