package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.security.AdminRequestResolver;
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

    public GroupPlayerController(
        PlayerQueryService playerQueryService,
        AdminRequestResolver adminRequestResolver
    ) {
        this.playerQueryService = playerQueryService;
        this.adminRequestResolver = adminRequestResolver;
    }

    @GetMapping("/{groupId}/players")
    public List<GroupPlayerResponse> getGroupPlayers(
        @PathVariable Long groupId,
        @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
        HttpServletRequest request
    ) {
        boolean adminRequest = adminRequestResolver.isAdminRequest(request);
        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(
            groupId,
            adminRequest && includeInactive
        );
        if (adminRequest) {
            return response;
        }

        return MmrMaskingMapper.maskGroupPlayers(response);
    }
}
