package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.GroupPlayerImportRequest;
import com.balancify.backend.api.group.dto.GroupPlayerImportResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupPlayerImportController {

    private final PlayerImportService playerImportService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;
    private final AccessControlService accessControlService;

    public GroupPlayerImportController(
        PlayerImportService playerImportService,
        AuthenticatedRequestResolver authenticatedRequestResolver,
        AccessControlService accessControlService
    ) {
        this.playerImportService = playerImportService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
        this.accessControlService = accessControlService;
    }

    @PostMapping("/{groupId}/players/import")
    public GroupPlayerImportResponse importPlayers(
        @PathVariable Long groupId,
        @RequestBody GroupPlayerImportRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity =
            authenticatedRequestResolver.resolve(httpRequest);
        return playerImportService.importPlayers(
            groupId,
            request,
            identity.email(),
            resolveActorNickname(identity)
        );
    }

    private String resolveActorNickname(AuthenticatedRequestResolver.ResolvedRequestIdentity identity) {
        if (identity == null || identity.email().isBlank()) {
            return null;
        }

        if (identity.nickname() != null && !identity.nickname().isBlank()) {
            return identity.nickname().trim();
        }

        String nickname = accessControlService.resolveAccessProfile(identity.email()).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
