package com.balancify.backend.api.access.dto;

import java.util.List;

public record AccessResultEditorListResponse(
    List<AccessEmailEntryResponse> resultEditors
) {
}
