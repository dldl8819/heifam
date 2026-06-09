package com.balancify.backend.api.group;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GroupPlayerAdminControllerTest {

    @Test
    void fallsBackToAccessProfileNicknameWhenIdentityNicknameIsMissing() {
        PlayerAdminService playerAdminService = mock(PlayerAdminService.class);
        MmrAccessRequestResolver mmrAccessRequestResolver = mock(MmrAccessRequestResolver.class);
        AuthenticatedRequestResolver authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        GroupPlayerAdminController controller = new GroupPlayerAdminController(
            playerAdminService,
            mmrAccessRequestResolver,
            authenticatedRequestResolver,
            accessControlService
        );
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        GroupPlayerUpdateRequest request = new GroupPlayerUpdateRequest(
            null,
            "PTZ",
            null,
            null,
            null,
            null,
            null
        );

        when(authenticatedRequestResolver.resolve(httpRequest))
            .thenReturn(new AuthenticatedRequestResolver.ResolvedRequestIdentity("operator@example.test", "", true));
        when(accessControlService.resolveAccessProfile("operator@example.test"))
            .thenReturn(new AccessControlService.AccessProfile(
                "operator@example.test",
                "OpsUser",
                "ADMIN",
                true,
                false,
                true,
                true,
                null
            ));

        controller.updatePlayer(1L, 10L, request, httpRequest);

        verify(playerAdminService).updatePlayer(
            eq(1L),
            eq(10L),
            eq(request),
            eq("operator@example.test"),
            eq("OpsUser")
        );
    }
}
