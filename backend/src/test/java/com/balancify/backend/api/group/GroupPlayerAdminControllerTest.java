package com.balancify.backend.api.group;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerAdminService;
import com.balancify.backend.service.exception.PlayerEditForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
            eq("OpsUser"),
            isNull()
        );
    }

    @Test
    void passesVerifiedAuthUserIdFromJwtToService() {
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
        UUID authUserId = UUID.randomUUID();

        when(authenticatedRequestResolver.resolve(httpRequest))
            .thenReturn(new AuthenticatedRequestResolver.ResolvedRequestIdentity(
                "member@example.test",
                "MemberUser",
                true,
                authUserId.toString()
            ));

        controller.updatePlayer(1L, 10L, request, httpRequest);

        verify(playerAdminService).updatePlayer(
            eq(1L),
            eq(10L),
            eq(request),
            eq("member@example.test"),
            eq("MemberUser"),
            eq(authUserId)
        );
    }

    @Test
    void doesNotTrustUserIdWhenJwtIsNotVerified() {
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
            .thenReturn(new AuthenticatedRequestResolver.ResolvedRequestIdentity(
                "member@example.test",
                "MemberUser",
                false,
                UUID.randomUUID().toString()
            ));

        controller.updatePlayer(1L, 10L, request, httpRequest);

        verify(playerAdminService).updatePlayer(
            eq(1L),
            eq(10L),
            eq(request),
            eq("member@example.test"),
            eq("MemberUser"),
            isNull()
        );
    }

    @Test
    void mapsPlayerEditForbiddenExceptionToHttp403() {
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
            .thenReturn(new AuthenticatedRequestResolver.ResolvedRequestIdentity(
                "member@example.test",
                "MemberUser",
                true,
                UUID.randomUUID().toString()
            ));
        doThrow(new PlayerEditForbiddenException("본인 선수 정보만 수정할 수 있습니다."))
            .when(playerAdminService)
            .updatePlayer(eq(1L), eq(10L), eq(request), eq("member@example.test"), eq("MemberUser"), any());

        assertThatThrownBy(() -> controller.updatePlayer(1L, 10L, request, httpRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
    }
}
