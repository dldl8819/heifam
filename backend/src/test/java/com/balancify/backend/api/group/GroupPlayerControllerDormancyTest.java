package com.balancify.backend.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupDormantPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerLastParticipationResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerActivityQueryService;
import com.balancify.backend.service.PlayerQueryService;
import com.balancify.backend.service.PlayerRaceStatsQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GroupPlayerControllerDormancyTest {

    private static final String REQUESTER_PLACEHOLDER = "YOUR_USERNAME";

    private final PlayerQueryService playerQueryService = mock(PlayerQueryService.class);
    private final PlayerActivityQueryService playerActivityQueryService = mock(PlayerActivityQueryService.class);
    private final PlayerRaceStatsQueryService playerRaceStatsQueryService = mock(PlayerRaceStatsQueryService.class);
    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final AuthenticatedRequestResolver authenticatedRequestResolver =
        mock(AuthenticatedRequestResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private GroupPlayerController controller;

    @BeforeEach
    void setUp() {
        controller = new GroupPlayerController(
            playerQueryService,
            playerActivityQueryService,
            playerRaceStatsQueryService,
            accessControlService,
            authenticatedRequestResolver
        );
        when(authenticatedRequestResolver.resolve(request)).thenReturn(
            new AuthenticatedRequestResolver.ResolvedRequestIdentity(
                REQUESTER_PLACEHOLDER,
                "",
                true
            )
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void allowsAdminAndSuperAdminToReadDormantRoster(boolean superAdmin) {
        allowAdmin(superAdmin);
        List<GroupDormantPlayerResponse> expected = List.of(
            new GroupDormantPlayerResponse(11L, "PLAYER_PLACEHOLDER")
        );
        when(playerActivityQueryService.getDormantPlayers(1L)).thenReturn(expected);

        assertThat(controller.getDormantGroupPlayers(1L, request)).isEqualTo(expected);
        verify(playerActivityQueryService).getDormantPlayers(1L);
    }

    @Test
    void rejectsMemberDormantRosterRequest() {
        allowMember();

        assertForbidden(() -> controller.getDormantGroupPlayers(1L, request));
        verify(playerActivityQueryService, never()).getDormantPlayers(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void allowsAdminAndSuperAdminToReadLastParticipation(boolean superAdmin) {
        allowAdmin(superAdmin);
        GroupPlayerLastParticipationResponse expected = new GroupPlayerLastParticipationResponse(
            OffsetDateTime.parse("2026-07-20T12:30:00Z")
        );
        when(playerActivityQueryService.getLastParticipation(1L, 11L)).thenReturn(expected);

        assertThat(controller.getPlayerLastParticipation(1L, 11L, request)).isEqualTo(expected);
        verify(playerActivityQueryService).getLastParticipation(1L, 11L);
    }

    @Test
    void rejectsMemberLastParticipationRequest() {
        allowMember();

        assertForbidden(() -> controller.getPlayerLastParticipation(1L, 11L, request));
        verify(playerActivityQueryService, never()).getLastParticipation(1L, 11L);
    }

    @Test
    void returnsNotFoundWithoutDisclosingHiddenPlayerState() {
        allowAdmin(false);
        when(playerActivityQueryService.getLastParticipation(1L, 11L))
            .thenThrow(new NoSuchElementException("Player not found"));

        assertThatThrownBy(() -> controller.getPlayerLastParticipation(1L, 11L, request))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    private void allowAdmin(boolean superAdmin) {
        when(accessControlService.resolveAccessProfile(REQUESTER_PLACEHOLDER)).thenReturn(
            new AccessControlService.AccessProfile(
                REQUESTER_PLACEHOLDER,
                null,
                superAdmin ? "SUPER_ADMIN" : "ADMIN",
                true,
                superAdmin,
                true,
                false,
                null
            )
        );
    }

    private void allowMember() {
        when(accessControlService.resolveAccessProfile(REQUESTER_PLACEHOLDER)).thenReturn(
            new AccessControlService.AccessProfile(
                REQUESTER_PLACEHOLDER,
                null,
                "MEMBER",
                false,
                false,
                true,
                false,
                null
            )
        );
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }
}
