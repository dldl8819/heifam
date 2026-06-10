package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTierBoardResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        if (accessProfile.superAdmin()) {
            return response;
        }
        if (accessProfile.canViewMmr()) {
            return MmrMaskingMapper.maskGroupPlayersForMmrViewer(response);
        }
        if (accessProfile.admin()) {
            return MmrMaskingMapper.maskGroupPlayersForAdmin(response);
        }

        return MmrMaskingMapper.maskGroupPlayersForMember(response);
    }

    @GetMapping("/{groupId}/players/tier-board")
    public List<GroupPlayerTierBoardResponse> getGroupPlayerTierBoard(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );
        if (!accessProfile.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can access tier board data");
        }

        return playerQueryService.getGroupPlayerTierBoard(groupId);
    }
}
