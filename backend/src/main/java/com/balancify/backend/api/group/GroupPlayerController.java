package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.security.AdminRequestResolver;
import com.balancify.backend.security.SuperAdminRequestResolver;
import com.balancify.backend.service.PlayerQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupPlayerController {

    private final PlayerQueryService playerQueryService;
    private final AdminRequestResolver adminRequestResolver;
    private final SuperAdminRequestResolver superAdminRequestResolver;

    public GroupPlayerController(
        PlayerQueryService playerQueryService,
        AdminRequestResolver adminRequestResolver,
        SuperAdminRequestResolver superAdminRequestResolver
    ) {
        this.playerQueryService = playerQueryService;
        this.adminRequestResolver = adminRequestResolver;
        this.superAdminRequestResolver = superAdminRequestResolver;
    }

    @GetMapping("/{groupId}/players")
    public List<GroupPlayerResponse> getGroupPlayers(
        @PathVariable Long groupId,
        @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
        HttpServletRequest request
    ) {
        boolean adminRequest = adminRequestResolver.isAdminRequest(request);
        boolean superAdminRequest = superAdminRequestResolver.isSuperAdminRequest(request);
        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(
            groupId,
            adminRequest && includeInactive
        );
        if (superAdminRequest) {
            return response;
        }

        return MmrMaskingMapper.maskGroupPlayers(response);
    }
}
