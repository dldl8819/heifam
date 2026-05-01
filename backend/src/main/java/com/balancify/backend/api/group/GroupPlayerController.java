package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
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
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupPlayerController(
        PlayerQueryService playerQueryService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.playerQueryService = playerQueryService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/{groupId}/players")
    public List<GroupPlayerResponse> getGroupPlayers(
        @PathVariable Long groupId,
        @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
        HttpServletRequest request
    ) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );
        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(
            groupId,
            accessProfile.admin() && includeInactive
        );
        if (accessProfile.canViewMmr()) {
            return response;
        }
        if (accessProfile.admin()) {
            return MmrMaskingMapper.maskGroupPlayersForAdmin(response);
        }

        return MmrMaskingMapper.maskGroupPlayersForMember(response);
    }
}
