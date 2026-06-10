package com.balancify.backend.api.group;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.security.SuperAdminRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GroupPlayerAdminControllerTest {

    @Test
    void fallsBackToAccessProfileNicknameWhenIdentityNicknameIsMissing() {
        PlayerAdminService playerAdminService = mock(PlayerAdminService.class);
        MmrAccessRequestResolver mmrAccessRequestResolver = mock(MmrAccessRequestResolver.class);
        SuperAdminRequestResolver superAdminRequestResolver = mock(SuperAdminRequestResolver.class);
        AuthenticatedRequestResolver authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        GroupPlayerAdminController controller = new GroupPlayerAdminController(
            playerAdminService,
            mmrAccessRequestResolver,
            superAdminRequestResolver,
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

    @Test
    void allowsSuperAdminToUpdateDormancyMmrFloorTier() {
        PlayerAdminService playerAdminService = mock(PlayerAdminService.class);
        MmrAccessRequestResolver mmrAccessRequestResolver = mock(MmrAccessRequestResolver.class);
        SuperAdminRequestResolver superAdminRequestResolver = mock(SuperAdminRequestResolver.class);
        AuthenticatedRequestResolver authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        GroupPlayerAdminController controller = new GroupPlayerAdminController(
            playerAdminService,
            mmrAccessRequestResolver,
            superAdminRequestResolver,
            authenticatedRequestResolver,
            accessControlService
        );
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        GroupPlayerUpdateRequest request = new GroupPlayerUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "B+"
        );

        when(superAdminRequestResolver.isSuperAdminRequest(httpRequest)).thenReturn(true);
        when(authenticatedRequestResolver.resolve(httpRequest))
            .thenReturn(new AuthenticatedRequestResolver.ResolvedRequestIdentity("operator@example.test", "OpsUser", true));

        controller.updatePlayer(1L, 10L, request, httpRequest);

        verify(playerAdminService).updatePlayer(
            eq(1L),
            eq(10L),
            eq(request),
            eq("operator@example.test"),
            eq("OpsUser")
        );
    }

    @Test
    void rejectsDormancyMmrFloorTierUpdateWhenRequesterIsNotSuperAdmin() {
        PlayerAdminService playerAdminService = mock(PlayerAdminService.class);
        MmrAccessRequestResolver mmrAccessRequestResolver = mock(MmrAccessRequestResolver.class);
        SuperAdminRequestResolver superAdminRequestResolver = mock(SuperAdminRequestResolver.class);
        AuthenticatedRequestResolver authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        GroupPlayerAdminController controller = new GroupPlayerAdminController(
            playerAdminService,
            mmrAccessRequestResolver,
            superAdminRequestResolver,
            authenticatedRequestResolver,
            accessControlService
        );
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        GroupPlayerUpdateRequest request = new GroupPlayerUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "B"
        );

        when(superAdminRequestResolver.isSuperAdminRequest(httpRequest)).thenReturn(false);

        assertThatThrownBy(() -> controller.updatePlayer(1L, 10L, request, httpRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(playerAdminService, never()).updatePlayer(
            eq(1L),
            eq(10L),
            eq(request),
            eq("operator@example.test"),
            eq("OpsUser")
        );
    }
}
