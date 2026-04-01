package com.balancify.backend.api.access.dto;

import java.util.List;

public record AccessAdminListResponse(
    List<AccessEmailEntryResponse> superAdmins,
    List<AccessEmailEntryResponse> admins
) {
}
