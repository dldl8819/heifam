package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupDormantPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerLastParticipationResponse;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatsResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTierBoardResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerActivityQueryService;
import com.balancify.backend.service.PlayerQueryService;
import com.balancify.backend.service.PlayerRaceStatsQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
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
    private final PlayerActivityQueryService playerActivityQueryService;
    private final PlayerRaceStatsQueryService playerRaceStatsQueryService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupPlayerController(
        PlayerQueryService playerQueryService,
        PlayerActivityQueryService playerActivityQueryService,
        PlayerRaceStatsQueryService playerRaceStatsQueryService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.playerQueryService = playerQueryService;
        this.playerActivityQueryService = playerActivityQueryService;
        this.playerRaceStatsQueryService = playerRaceStatsQueryService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/{groupId}/players/dormant")
    public List<GroupDormantPlayerResponse> getDormantGroupPlayers(
        @PathVariable Long groupId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        requireAdmin(request, "Only admins can access dormant player data");
        preventSensitiveResponseCaching(response);
        return playerActivityQueryService.getDormantPlayers(groupId);
    }

    @GetMapping("/{groupId}/players/{playerId}/last-participation")
    public GroupPlayerLastParticipationResponse getPlayerLastParticipation(
        @PathVariable Long groupId,
        @PathVariable Long playerId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        requireAdmin(request, "Only admins can access player participation data");
        preventSensitiveResponseCaching(response);
        try {
            return playerActivityQueryService.getLastParticipation(groupId, playerId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{groupId}/players")
    public List<GroupPlayerResponse> getGroupPlayers(
        @PathVariable Long groupId,
        @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
        HttpServletRequest request,
        HttpServletResponse httpResponse
    ) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity =
            authenticatedRequestResolver.resolve(request);
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            identity.email()
        );
        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(
            groupId,
            accessProfile.admin() && includeInactive,
            resolveVerifiedAuthUserId(identity)
        );
        if (accessProfile.admin() && includeInactive) {
            preventSensitiveResponseCaching(httpResponse);
        }
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

    @GetMapping("/{groupId}/players/race-stats")
    public List<GroupPlayerRaceStatsResponse> getGroupPlayerRaceStats(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );
        if (!accessProfile.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is not allowed");
        }

        return playerRaceStatsQueryService.getGroupPlayerRaceStats(groupId);
    }

    @GetMapping("/{groupId}/players/{playerId}/race-stats")
    public GroupPlayerRaceStatsResponse getGroupPlayerRaceStats(
        @PathVariable Long groupId,
        @PathVariable Long playerId,
        HttpServletRequest request
    ) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );
        if (!accessProfile.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is not allowed");
        }

        try {
            return playerRaceStatsQueryService.getGroupPlayerRaceStats(groupId, playerId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{groupId}/players/{playerId}/race-stats/monthly")
    public GroupPlayerRaceStatsResponse getGroupPlayerMonthlyRaceStats(
        @PathVariable Long groupId,
        @PathVariable Long playerId,
        HttpServletRequest request
    ) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );
        if (!accessProfile.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is not allowed");
        }

        try {
            return playerRaceStatsQueryService.getGroupPlayerMonthlyRaceStats(groupId, playerId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
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

    private UUID resolveVerifiedAuthUserId(AuthenticatedRequestResolver.ResolvedRequestIdentity identity) {
        if (identity == null || !identity.jwtVerified() || identity.userId() == null || identity.userId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(identity.userId().trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void requireAdmin(HttpServletRequest request, String message) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );
        if (!accessProfile.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void preventSensitiveResponseCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }
}
