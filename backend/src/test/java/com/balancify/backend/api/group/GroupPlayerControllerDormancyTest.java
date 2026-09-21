package com.balancify.backend.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupDormantPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerLastParticipationResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatsResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerActivityQueryService;
import com.balancify.backend.service.PlayerQueryService;
import com.balancify.backend.service.PlayerRaceStatsQueryService;
import com.balancify.backend.service.PlayerTeammateStatsQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class GroupPlayerControllerDormancyTest {

    private static final String REQUESTER_PLACEHOLDER = "YOUR_USERNAME";
    private static final UUID ACCOUNT_PLACEHOLDER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final PlayerQueryService playerQueryService = mock(PlayerQueryService.class);
    private final PlayerActivityQueryService playerActivityQueryService = mock(PlayerActivityQueryService.class);
    private final PlayerRaceStatsQueryService playerRaceStatsQueryService = mock(PlayerRaceStatsQueryService.class);
    private final PlayerTeammateStatsQueryService playerTeammateStatsQueryService =
        mock(PlayerTeammateStatsQueryService.class);
    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final AuthenticatedRequestResolver authenticatedRequestResolver =
        mock(AuthenticatedRequestResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private GroupPlayerController controller;

    @BeforeEach
    void setUp() {
        controller = new GroupPlayerController(
            playerQueryService,
            playerActivityQueryService,
            playerRaceStatsQueryService,
            playerTeammateStatsQueryService,
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

        assertThat(controller.getDormantGroupPlayers(1L, request, response)).isEqualTo(expected);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        verify(playerActivityQueryService).getDormantPlayers(1L);
    }

    @Test
    void rejectsMemberDormantRosterRequest() {
        allowMember();

        assertForbidden(() -> controller.getDormantGroupPlayers(1L, request, response));
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

        assertThat(controller.getPlayerLastParticipation(1L, 11L, request, response)).isEqualTo(expected);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        verify(playerActivityQueryService).getLastParticipation(1L, 11L);
    }

    @Test
    void rejectsMemberLastParticipationRequest() {
        allowMember();

        assertForbidden(() -> controller.getPlayerLastParticipation(1L, 11L, request, response));
        verify(playerActivityQueryService, never()).getLastParticipation(1L, 11L);
    }

    @Test
    void returnsNotFoundWithoutDisclosingHiddenPlayerState() {
        allowAdmin(false);
        when(playerActivityQueryService.getLastParticipation(1L, 11L))
            .thenThrow(new NoSuchElementException("Player not found"));

        assertThatThrownBy(() -> controller.getPlayerLastParticipation(1L, 11L, request, response))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    @Test
    void givesAdminsTheTeammateStatsOfAnyPlayer() {
        allowAdmin(false);
        GroupPlayerTeammateStatsResponse expected = teammateStats();
        when(playerTeammateStatsQueryService.getTeammateStats(1L, 11L)).thenReturn(expected);

        assertThat(controller.getGroupPlayerTeammateStats(1L, 11L, request)).isEqualTo(expected);
        verify(playerTeammateStatsQueryService, never()).isOwnPlayer(anyLong(), anyLong(), any());
        verify(playerTeammateStatsQueryService, never()).getOwnTeammateStats(1L, 11L);
    }

    @Test
    void letsAMemberReadTheirOwnTeammateStats() {
        allowMember();
        signedInAs(ACCOUNT_PLACEHOLDER);
        GroupPlayerTeammateStatsResponse expected = teammateStats();
        when(playerTeammateStatsQueryService.isOwnPlayer(1L, 11L, ACCOUNT_PLACEHOLDER)).thenReturn(true);
        when(playerTeammateStatsQueryService.getOwnTeammateStats(1L, 11L)).thenReturn(expected);

        assertThat(controller.getGroupPlayerTeammateStats(1L, 11L, request)).isEqualTo(expected);
        verify(playerTeammateStatsQueryService, never()).getTeammateStats(1L, 11L);
    }

    @Test
    void rejectsAMemberReadingAnotherPlayersTeammateStats() {
        allowMember();
        signedInAs(ACCOUNT_PLACEHOLDER);
        when(playerTeammateStatsQueryService.isOwnPlayer(1L, 11L, ACCOUNT_PLACEHOLDER)).thenReturn(false);

        assertForbidden(() -> controller.getGroupPlayerTeammateStats(1L, 11L, request));
        verify(playerTeammateStatsQueryService, never()).getOwnTeammateStats(1L, 11L);
        verify(playerTeammateStatsQueryService, never()).getTeammateStats(1L, 11L);
    }

    private void signedInAs(UUID accountId) {
        when(authenticatedRequestResolver.resolve(request)).thenReturn(
            new AuthenticatedRequestResolver.ResolvedRequestIdentity(
                REQUESTER_PLACEHOLDER,
                "",
                true,
                accountId.toString()
            )
        );
    }

    private GroupPlayerTeammateStatsResponse teammateStats() {
        return new GroupPlayerTeammateStatsResponse(
            11L,
            "PLAYER_PLACEHOLDER",
            2,
            1,
            3,
            66.67,
            List.of(new GroupPlayerTeammateStatResponse(12L, "TEAMMATE_PLACEHOLDER", 2, 0, 2, 100.0, 2))
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
