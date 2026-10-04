package com.balancify.backend.api.group.dto;

import java.util.List;

public record NoticeListResponse(
    List<NoticeListItemResponse> notices,
    int readCount,
    int unreadCount
) {
}
