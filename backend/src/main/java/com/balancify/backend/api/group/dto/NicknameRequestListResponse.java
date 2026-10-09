package com.balancify.backend.api.group.dto;

import java.util.List;

public record NicknameRequestListResponse(
    // The reader's own requests, newest first.
    List<NicknameRequestResponse> mine,
    // Everyone's requests, those still waiting first; empty unless the reader is an admin.
    List<NicknameRequestResponse> received,
    boolean admin,
    // The reader's nickname as the site shows it now; null when the account has none.
    String currentNickname
) {
}
