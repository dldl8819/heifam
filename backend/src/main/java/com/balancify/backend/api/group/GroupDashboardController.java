package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.security.AdminRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.DashboardQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupDashboardController {

    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";
    private static final String USER_NICKNAME_HEADER = "X-USER-NICKNAME";

    private final DashboardQueryService dashboardQueryService;
    private final AdminRequestResolver adminRequestResolver;
    private final AccessControlService accessControlService;

    public GroupDashboardController(
        DashboardQueryService dashboardQueryService,
        AdminRequestResolver adminRequestResolver,
        AccessControlService accessControlService
    ) {
        this.dashboardQueryService = dashboardQueryService;
        this.adminRequestResolver = adminRequestResolver;
        this.accessControlService = accessControlService;
    }

    @GetMapping("/{groupId}/dashboard")
    public GroupDashboardResponse getGroupDashboard(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        String requestEmail = safeTrim(request == null ? null : request.getHeader(USER_EMAIL_HEADER));
        String requestNickname = safeTrim(request == null ? null : request.getHeader(USER_NICKNAME_HEADER));
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(requestEmail);
        if (!accessProfile.superAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only super admins can access dashboard data");
        }
        if (!adminRequestResolver.isAdminRequest(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin key is required for dashboard data");
        }

        String resolvedNickname = safeTrim(accessProfile.nickname());
        String effectiveNickname = resolvedNickname.isEmpty() ? requestNickname : resolvedNickname;
        return dashboardQueryService.getGroupDashboard(groupId, effectiveNickname);
    }

    private String safeTrim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.contains("%")) {
            return trimmed;
        }
        try {
            return URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }
}
