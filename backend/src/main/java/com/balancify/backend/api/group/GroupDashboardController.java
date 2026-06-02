package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.security.AdminRequestResolver;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.DashboardQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupDashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final AdminRequestResolver adminRequestResolver;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;
    private final boolean dashboardEnabled;

    public GroupDashboardController(
        DashboardQueryService dashboardQueryService,
        AdminRequestResolver adminRequestResolver,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver,
        @Value("${balancify.dashboard.enabled:false}") boolean dashboardEnabled
    ) {
        this.dashboardQueryService = dashboardQueryService;
        this.adminRequestResolver = adminRequestResolver;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
        this.dashboardEnabled = dashboardEnabled;
    }

    @GetMapping("/{groupId}/dashboard")
    public GroupDashboardResponse getGroupDashboard(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        if (!dashboardEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dashboard is temporarily disabled");
        }

        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        String requestEmail = safeTrim(identity.email());
        String requestNickname = safeTrim(identity.nickname());
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(requestEmail);
        if (!accessProfile.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can access dashboard data");
        }
        if (!adminRequestResolver.isAdminRequest(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin key is required for dashboard data");
        }

        String resolvedNickname = safeTrim(accessProfile.nickname());
        String effectiveNickname = resolvedNickname.isEmpty() ? requestNickname : resolvedNickname;
        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(groupId, effectiveNickname);
        if (accessProfile.canViewMmr()) {
            return response;
        }

        return MmrMaskingMapper.maskDashboard(response);
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
