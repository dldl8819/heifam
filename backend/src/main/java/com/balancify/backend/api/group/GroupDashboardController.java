package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.security.AdminRequestResolver;
import com.balancify.backend.service.DashboardQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupDashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final AdminRequestResolver adminRequestResolver;

    public GroupDashboardController(
        DashboardQueryService dashboardQueryService,
        AdminRequestResolver adminRequestResolver
    ) {
        this.dashboardQueryService = dashboardQueryService;
        this.adminRequestResolver = adminRequestResolver;
    }

    @GetMapping("/{groupId}/dashboard")
    public GroupDashboardResponse getGroupDashboard(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(groupId);
        if (adminRequestResolver.isAdminRequest(request)) {
            return response;
        }

        return MmrMaskingMapper.maskDashboard(response);
    }
}
