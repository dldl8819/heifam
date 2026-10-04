package com.balancify.backend.api.notification.dto;

import java.util.List;

/** The latest notifications the account may see, newest first, and how many of them are unread. */
public record NotificationListResponse(List<NotificationItemResponse> notifications, int unreadCount) {
}
