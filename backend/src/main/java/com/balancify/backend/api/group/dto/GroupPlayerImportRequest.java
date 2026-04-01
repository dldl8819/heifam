package com.balancify.backend.api.group.dto;

import java.util.List;

public record GroupPlayerImportRequest(
    List<GroupPlayerImportRowRequest> players
) {
}
