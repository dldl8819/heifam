package com.balancify.backend.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.balancify.backend.api.HealthController;
import com.balancify.backend.api.admin.AdminRatingController;
import com.balancify.backend.api.group.GroupMatchAdminController;
import com.balancify.backend.api.group.GroupMatchController;
import com.balancify.backend.api.group.GroupDashboardController;
import com.balancify.backend.api.group.GroupPlayerController;
import com.balancify.backend.api.group.GroupPlayerAdminController;
import com.balancify.backend.api.group.GroupPlayerImportController;
import com.balancify.backend.api.group.GroupRankingController;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.api.group.dto.DashboardKpiSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardTopRankingPreviewItemResponse;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerImportResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchPlayerResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.api.match.MatchImportController;
import com.balancify.backend.api.match.MatchBalanceController;
import com.balancify.backend.api.match.dto.MatchImportResponse;
import com.balancify.backend.api.match.MatchResultController;
import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultParticipantResponse;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalancePenaltySummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRaceSummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceWaitingPlayerResponse;
import com.balancify.backend.service.GroupMatchAdminService;
import com.balancify.backend.service.MatchQueryService;
import com.balancify.backend.service.MatchImportService;
import com.balancify.backend.service.MatchResultService;
import com.balancify.backend.service.ManualMatchService;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.DashboardQueryService;
import com.balancify.backend.service.MultiMatchBalancingService;
import com.balancify.backend.service.PlayerAdminService;
import com.balancify.backend.service.PlayerQueryService;
import com.balancify.backend.service.PlayerImportService;
import com.balancify.backend.service.RankingService;
import com.balancify.backend.service.RatingRecalculationService;
import com.balancify.backend.service.TeamBalancingService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    MatchResultController.class,
    MatchImportController.class,
    MatchBalanceController.class,
    AdminRatingController.class,
    HealthController.class,
    GroupDashboardController.class,
    GroupMatchController.class,
    GroupPlayerController.class,
    GroupRankingController.class,
    GroupPlayerImportController.class,
    GroupPlayerAdminController.class,
    GroupMatchAdminController.class
})
@Import({ AdminKeyFilter.class, ServiceAccessFilter.class, AdminKeyProperties.class })
@TestPropertySource(properties = {
    "balancify.admin.emails=admin@hei.gg,ops@hei.gg",
    "balancify.admin.super-emails=superadmin@hei.gg",
    "balancify.auth.allow-email-header-fallback=true",
    "balancify.auth.require-jwt=false"
})
class AdminKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchResultService matchResultService;

    @MockBean
    private ManualMatchService manualMatchService;

    @MockBean
    private MatchImportService matchImportService;

    @MockBean
    private PlayerImportService playerImportService;

    @MockBean
    private PlayerAdminService playerAdminService;

    @MockBean
    private GroupMatchAdminService groupMatchAdminService;

    @MockBean
    private MatchQueryService matchQueryService;

    @MockBean
    private DashboardQueryService dashboardQueryService;

    @MockBean
    private PlayerQueryService playerQueryService;

    @MockBean
    private RankingService rankingService;

    @MockBean
    private TeamBalancingService teamBalancingService;

    @MockBean
    private MultiMatchBalancingService multiMatchBalancingService;

    @MockBean
    private RatingRecalculationService ratingRecalculationService;

    @MockBean
    private AdminRequestResolver adminRequestResolver;

    @MockBean
    private SuperAdminRequestResolver superAdminRequestResolver;

    @MockBean
    private AccessControlService accessControlService;

    @MockBean
    private AuthenticatedRequestResolver authenticatedRequestResolver;

    @BeforeEach
    void setUp() {
        Set<String> admins = Set.of("admin@hei.gg", "ops@hei.gg");
        Set<String> superAdmins = Set.of("superadmin@hei.gg");
        Set<String> allowed = Set.of("admin@hei.gg", "ops@hei.gg", "superadmin@hei.gg", "member@hei.gg");

        when(accessControlService.isAdminEmail(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            String email = argument == null ? "" : argument.toString().trim().toLowerCase(Locale.ROOT);
            return admins.contains(email) || superAdmins.contains(email);
        });
        when(accessControlService.isSuperAdminEmail(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            String email = argument == null ? "" : argument.toString().trim().toLowerCase(Locale.ROOT);
            return superAdmins.contains(email);
        });
        when(accessControlService.isServiceAccessAllowed(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            String email = argument == null ? "" : argument.toString().trim().toLowerCase(Locale.ROOT);
            return allowed.contains(email);
        });
        when(accessControlService.resolveAccessProfile(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            String email = argument == null ? "" : argument.toString().trim().toLowerCase(Locale.ROOT);
            boolean isSuperAdmin = superAdmins.contains(email);
            boolean isAdmin = admins.contains(email) || isSuperAdmin;
            boolean isAllowed = allowed.contains(email);
            String nickname = email.contains("@") ? email.substring(0, email.indexOf('@')) : null;
            String role = isSuperAdmin ? "SUPER_ADMIN" : isAdmin ? "ADMIN" : isAllowed ? "MEMBER" : "BLOCKED";
            return new AccessControlService.AccessProfile(
                email,
                nickname,
                role,
                isAdmin,
                isSuperAdmin,
                isAllowed,
                null
            );
        });
        when(authenticatedRequestResolver.resolve(any(HttpServletRequest.class))).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            String email = request.getHeader("X-USER-EMAIL");
            if (email == null || email.isBlank()) {
                return AuthenticatedRequestResolver.ResolvedRequestIdentity.empty();
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            String nickname = request.getHeader("X-USER-NICKNAME");
            String normalizedNickname = nickname == null ? "" : nickname.trim();
            return new AuthenticatedRequestResolver.ResolvedRequestIdentity(normalizedEmail, normalizedNickname, true);
        });
        when(superAdminRequestResolver.isSuperAdminRequest(any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            String email = request.getHeader("X-USER-EMAIL");
            if (email == null || email.isBlank()) {
                return false;
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            return superAdmins.contains(normalizedEmail);
        });
    }

    @Test
    void returnsForbiddenWhenUserEmailHeaderIsMissingForMatchResult() throws Exception {
        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenWhenUserEmailIsNotAllowedForMatchResult() throws Exception {
        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .header("X-USER-EMAIL", "guest@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchResultWhenUserEmailIsAllowedMember() throws Exception {
        when(accessControlService.resolveAccessProfile(eq("member@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "member@hei.gg",
                    "민식",
                    "MEMBER",
                    false,
                    false,
                    true,
                    null
                )
            );
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any(), anyBoolean()))
            .thenReturn(
                new MatchResultResponse(
                    1L,
                    "HOME",
                    32,
                    0.5,
                    0.5,
                    List.of(
                        new MatchResultParticipantResponse(
                            10L,
                            "alpha",
                            "HOME",
                            1200,
                            1216,
                            16
                        )
                    )
                )
            );
        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .header("X-USER-NICKNAME", "김원섭")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeExpectedWinRate").doesNotExist())
            .andExpect(jsonPath("$.awayExpectedWinRate").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrBefore").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrAfter").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrDelta").doesNotExist());

        verify(matchResultService).processMatchResult(
            eq(1L),
            any(MatchResultRequest.class),
            eq("member@hei.gg"),
            eq("민식"),
            eq(false)
        );
    }

    @Test
    void omitsRecordedByNicknameWhenAccessProfileHasNoNickname() throws Exception {
        when(accessControlService.resolveAccessProfile(eq("member@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "member@hei.gg",
                    null,
                    "MEMBER",
                    false,
                    false,
                    true,
                    null
                )
            );
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any(), anyBoolean()))
            .thenReturn(new MatchResultResponse(1L, "HOME", 32, 0.5, 0.5, List.of()));
        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .header("X-USER-NICKNAME", "김원섭")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isOk());

        verify(matchResultService).processMatchResult(
            eq(1L),
            any(MatchResultRequest.class),
            eq("member@hei.gg"),
            isNull(),
            eq(false)
        );
    }

    @Test
    void returnsForbiddenWhenAdminEmailHeaderIsMissingForMatchResultPatch() throws Exception {
        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"AWAY\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchResultPatchWhenAdminEmailHeaderIsValid() throws Exception {
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any(), anyBoolean()))
            .thenReturn(
                new MatchResultResponse(
                    1L,
                    "AWAY",
                    32,
                    0.5,
                    0.5,
                    List.of()
                )
            );
        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"AWAY\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsMmrFieldsForSuperAdminMatchResult() throws Exception {
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any(), anyBoolean()))
            .thenReturn(
                new MatchResultResponse(
                    1L,
                    "HOME",
                    32,
                    0.67,
                    0.33,
                    List.of(
                        new MatchResultParticipantResponse(
                            10L,
                            "alpha",
                            "HOME",
                            1200,
                            1216,
                            16
                        )
                    )
                )
            );
        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeExpectedWinRate").value(0.67))
            .andExpect(jsonPath("$.awayExpectedWinRate").value(0.33))
            .andExpect(jsonPath("$.participants[0].mmrBefore").value(1200))
            .andExpect(jsonPath("$.participants[0].mmrAfter").value(1216))
            .andExpect(jsonPath("$.participants[0].mmrDelta").value(16));
    }

    @Test
    void returnsForbiddenForManualMatchCreateWithoutUserEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/matches/manual")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "groupId": 1,
                          "teamSize": 3,
                          "homePlayerIds": [1,2,3],
                          "awayPlayerIds": [4,5,6],
                          "winnerTeam": "HOME"
                        }
                        """)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsManualMatchCreateWithMemberEmailAndMasksMmrFields() throws Exception {
        when(manualMatchService.createManualMatch(any(), any(), any()))
            .thenReturn(
                new MatchResultResponse(
                    201L,
                    "HOME",
                    32,
                    0.52,
                    0.48,
                    List.of(
                        new MatchResultParticipantResponse(
                            10L,
                            "alpha",
                            "HOME",
                            1200,
                            1216,
                            16
                        )
                    )
                )
            );
        mockMvc
            .perform(
                post("/api/matches/manual")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .header("X-USER-NICKNAME", "민식")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "groupId": 1,
                          "teamSize": 3,
                          "homePlayerIds": [1,2,3],
                          "awayPlayerIds": [4,5,6],
                          "winnerTeam": "HOME"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchId").value(201))
            .andExpect(jsonPath("$.homeExpectedWinRate").doesNotExist())
            .andExpect(jsonPath("$.awayExpectedWinRate").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrBefore").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrAfter").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrDelta").doesNotExist());
    }

    @Test
    void allowsManualMatchCreateWithAdminEmail() throws Exception {
        when(manualMatchService.createManualMatch(any(), any(), any()))
            .thenReturn(
                new MatchResultResponse(
                    201L,
                    "HOME",
                    32,
                    0.52,
                    0.48,
                    List.of()
                )
            );
        mockMvc
            .perform(
                post("/api/matches/manual")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "groupId": 1,
                          "teamSize": 3,
                          "homePlayerIds": [1,2,3],
                          "awayPlayerIds": [4,5,6],
                          "winnerTeam": "HOME",
                          "note": "리겜 수동 입력"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchId").value(201));
    }

    @Test
    void allowsManualMatchCreateWithSuperAdminEmail() throws Exception {
        when(manualMatchService.createManualMatch(any(), any(), any()))
            .thenReturn(
                new MatchResultResponse(
                    301L,
                    "AWAY",
                    32,
                    0.41,
                    0.59,
                    List.of()
                )
            );
        when(adminRequestResolver.isAdminRequest(any())).thenReturn(true);

        mockMvc
            .perform(
                post("/api/matches/manual")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "groupId": 1,
                          "teamSize": 3,
                          "homePlayerIds": [1,2,3],
                          "awayPlayerIds": [4,5,6],
                          "winnerTeam": "AWAY"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchId").value(301))
            .andExpect(jsonPath("$.homeExpectedWinRate").value(0.41))
            .andExpect(jsonPath("$.awayExpectedWinRate").value(0.59));
    }

    @Test
    void returnsForbiddenForPlayersImportPathWithoutUserEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/players/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"players\":[]}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForPlayersImportPathWithNonAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/players/import")
                    .header("X-USER-EMAIL", "guest@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"players\":[]}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsPlayersImportPathWithAdminEmail() throws Exception {
        when(playerImportService.importPlayers(eq(1L), any()))
            .thenReturn(new GroupPlayerImportResponse(1, 1, 0, 0, List.of()));

        mockMvc
            .perform(
                post("/api/groups/1/players/import")
                    .header("X-USER-EMAIL", "ops@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"players\":[]}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForMatchesImportPathWithoutUserEmail() throws Exception {
        mockMvc
            .perform(post("/api/matches/import"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchesImportPathWithAdminEmail() throws Exception {
        when(matchImportService.importMatches(any()))
            .thenReturn(new MatchImportResponse(1, 1, 0, List.of()));

        mockMvc
            .perform(
                post("/api/matches/import")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[]")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForMatchesImportPathWhenOnlyAdminKeyIsProvided() throws Exception {
        mockMvc
            .perform(
                post("/api/matches/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[]")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForMatchDeleteWithoutAdminEmail() throws Exception {
        mockMvc
            .perform(delete("/api/matches/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchDeleteWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                delete("/api/matches/1")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForRatingRecalculationWithoutSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/rating/recalculate")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirm\":true}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsRatingRecalculationWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/rating/recalculate")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirm\":true}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForGroupMatchCreateWithoutAllowedEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/matches")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"homePlayerIds\":[1,2,3],\"awayPlayerIds\":[4,5,6]}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsGroupMatchCreateWithAllowedEmail() throws Exception {
        when(groupMatchAdminService.createMatch(eq(1L), any()))
            .thenReturn(new CreateGroupMatchResponse(100L, "CREATED", "ok"));

        mockMvc
            .perform(
                post("/api/groups/1/matches")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"homePlayerIds\":[1,2,3],\"awayPlayerIds\":[4,5,6]}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForGroupMatchCreateWhenEmailIsNotAllowed() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/matches")
                    .header("X-USER-EMAIL", "guest@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"homePlayerIds\":[1,2,3],\"awayPlayerIds\":[4,5,6]}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForPlayerUpdateWithoutAdminEmail() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"새닉네임\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsPlayerUpdateWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"새닉네임\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void hidesMmrFieldsFromMemberForGroupPlayers() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(false)))
            .thenReturn(
                List.of(
                    new GroupPlayerResponse(
                        10L,
                        "alpha",
                        "P",
                        "A",
                        1200,
                        "A",
                        1216,
                        2,
                        1,
                        3,
                        true
                    )
                )
            );
        mockMvc
            .perform(
                get("/api/groups/1/players")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].baseMmr").doesNotExist());
    }

    @Test
    void returnsMmrFieldsForSuperAdminForGroupPlayers() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(false)))
            .thenReturn(
                List.of(
                    new GroupPlayerResponse(
                        10L,
                        "alpha",
                        "P",
                        "A",
                        1200,
                        "A",
                        1216,
                        2,
                        1,
                        3,
                        true
                    )
                )
            );
        mockMvc
            .perform(
                get("/api/groups/1/players")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currentMmr").value(1216))
            .andExpect(jsonPath("$[0].baseMmr").value(1200));
    }

    @Test
    void returnsForbiddenForMemberForRanking() throws Exception {
        when(rankingService.getGroupRanking(eq(1L)))
            .thenReturn(
                List.of(
                    new RankingItemResponse(
                        1,
                        "alpha",
                        "P",
                        "A+",
                        1216,
                        2,
                        1,
                        3,
                        66.67,
                        "W2",
                        "WWL",
                        16
                    )
                )
            );
        mockMvc
            .perform(
                get("/api/groups/1/ranking")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForAdminForRanking() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ranking")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsRankingForSuperAdmin() throws Exception {
        when(rankingService.getGroupRanking(eq(1L)))
            .thenReturn(
                List.of(
                    new RankingItemResponse(
                        1,
                        "alpha",
                        "P",
                        "A+",
                        1216,
                        2,
                        1,
                        3,
                        66.67,
                        "W2",
                        "WWL",
                        16
                    )
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/ranking")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currentMmr").value(1216))
            .andExpect(jsonPath("$[0].mmrDelta").value(16));
    }

    @Test
    void rejectsDashboardForMemberSoNoMmrSummaryIsExposed() throws Exception {
        when(accessControlService.resolveAccessProfile(eq("member@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "member@hei.gg",
                    "member",
                    "MEMBER",
                    false,
                    false,
                    true,
                    null
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/dashboard")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .header("X-USER-NICKNAME", "member")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsDashboardForSuperAdminWhenAuthenticated() throws Exception {
        when(accessControlService.resolveAccessProfile(eq("superadmin@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "superadmin@hei.gg",
                    "superadmin",
                    "SUPER_ADMIN",
                    true,
                    true,
                    true,
                    null
                )
            );
        when(adminRequestResolver.isAdminRequest(any())).thenReturn(true);
        when(dashboardQueryService.getGroupDashboard(eq(1L), eq("superadmin")))
            .thenReturn(
                new GroupDashboardResponse(
                    24,
                    new DashboardKpiSummaryResponse(10, 1400, 1320.5, 12),
                    List.of(new DashboardTopRankingPreviewItemResponse(1, "alpha", "P", 1400, 75.0)),
                    null,
                    null,
                    null
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/dashboard")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .header("X-USER-NICKNAME", "superadmin")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpiSummary.topMmr").value(1400))
            .andExpect(jsonPath("$.topRankingPreview[0].currentMmr").value(1400));
    }

    @Test
    void hidesMmrFieldsFromAdminForRecentMatches() throws Exception {
        when(matchQueryService.getRecentMatches(eq(1L), any()))
            .thenReturn(
                List.of(
                    new GroupRecentMatchResponse(
                        77L,
                        OffsetDateTime.parse("2026-04-01T12:00:00Z"),
                        "COMPLETED",
                        "HOME",
                        OffsetDateTime.parse("2026-04-01T12:40:00Z"),
                        "운영진",
                        "PTZ",
                        "PTZ",
                        List.of(new GroupRecentMatchPlayerResponse(10L, "alpha", "HOME", 1200)),
                        List.of(new GroupRecentMatchPlayerResponse(20L, "bravo", "AWAY", 1184)),
                        3600,
                        3552,
                        48
                    )
                )
            );
        mockMvc
            .perform(
                get("/api/groups/1/matches/recent")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].homeMmr").doesNotExist())
            .andExpect(jsonPath("$[0].awayMmr").doesNotExist())
            .andExpect(jsonPath("$[0].mmrDiff").doesNotExist())
            .andExpect(jsonPath("$[0].homeTeam[0].mmr").doesNotExist())
            .andExpect(jsonPath("$[0].awayTeam[0].mmr").doesNotExist());
    }

    @Test
    void hidesMmrFieldsFromMemberForBalanceResponse() throws Exception {
        when(teamBalancingService.balance(any()))
            .thenReturn(
                new BalanceResponse(
                    3,
                    List.of(
                        new BalancePlayerDto(1L, "alpha", 1200),
                        new BalancePlayerDto(2L, "bravo", 1190),
                        new BalancePlayerDto(3L, "charlie", 1180)
                    ),
                    List.of(
                        new BalancePlayerDto(4L, "delta", 1170),
                        new BalancePlayerDto(5L, "echo", 1160),
                        new BalancePlayerDto(6L, "foxtrot", 1150)
                    ),
                    3570,
                    3480,
                    90,
                    0.61
                )
            );
        mockMvc
            .perform(
                post("/api/matches/balance")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupId\":1,\"playerIds\":[1,2,3,4,5,6],\"teamSize\":3}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeMmr").doesNotExist())
            .andExpect(jsonPath("$.awayMmr").doesNotExist())
            .andExpect(jsonPath("$.mmrDiff").doesNotExist())
            .andExpect(jsonPath("$.expectedHomeWinRate").value(0.61))
            .andExpect(jsonPath("$.homeTeam[0].mmr").doesNotExist())
            .andExpect(jsonPath("$.awayTeam[0].mmr").doesNotExist());
    }

    @Test
    void returnsMmrFieldsForSuperAdminForBalanceResponse() throws Exception {
        when(teamBalancingService.balance(any()))
            .thenReturn(
                new BalanceResponse(
                    3,
                    List.of(
                        new BalancePlayerDto(1L, "alpha", 1200),
                        new BalancePlayerDto(2L, "bravo", 1190),
                        new BalancePlayerDto(3L, "charlie", 1180)
                    ),
                    List.of(
                        new BalancePlayerDto(4L, "delta", 1170),
                        new BalancePlayerDto(5L, "echo", 1160),
                        new BalancePlayerDto(6L, "foxtrot", 1150)
                    ),
                    3570,
                    3480,
                    90,
                    0.61
                )
            );
        mockMvc
            .perform(
                post("/api/matches/balance")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupId\":1,\"playerIds\":[1,2,3,4,5,6],\"teamSize\":3}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeMmr").value(3570))
            .andExpect(jsonPath("$.awayMmr").value(3480))
            .andExpect(jsonPath("$.mmrDiff").value(90))
            .andExpect(jsonPath("$.expectedHomeWinRate").value(0.61))
            .andExpect(jsonPath("$.homeTeam[0].mmr").value(1200))
            .andExpect(jsonPath("$.awayTeam[0].mmr").value(1170));
    }

    @Test
    void returnsReadableMessageForBalanceBadRequest() throws Exception {
        when(teamBalancingService.balance(any()))
            .thenThrow(new IllegalArgumentException("선택한 종족 조합으로 매치를 구성할 수 없습니다"));
        mockMvc
            .perform(
                post("/api/matches/balance")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupId\":1,\"playerIds\":[1,2,3,4,5,6],\"teamSize\":3,\"raceComposition\":\"PPT\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("선택한 종족 조합으로 매치를 구성할 수 없습니다"))
            .andExpect(jsonPath("$.path").value("/api/matches/balance"));
    }

    @Test
    void hidesMmrFieldsFromMemberForMultiBalanceResponse() throws Exception {
        when(multiMatchBalancingService.balance(any()))
            .thenReturn(
                new MultiBalanceResponse(
                    "MMR_FIRST",
                    6,
                    6,
                    List.of(new MultiBalanceWaitingPlayerResponse(7L, "대기")),
                    1,
                    List.of(
                        new MultiBalanceMatchResponse(
                            1,
                            "3v3",
                            3,
                            List.of(
                                new BalancePlayerDto(1L, "alpha", 1200),
                                new BalancePlayerDto(2L, "bravo", 1190),
                                new BalancePlayerDto(3L, "charlie", 1180)
                            ),
                            List.of(
                                new BalancePlayerDto(4L, "delta", 1170),
                                new BalancePlayerDto(5L, "echo", 1160),
                                new BalancePlayerDto(6L, "foxtrot", 1150)
                            ),
                            3570,
                            3480,
                            90,
                            0.61,
                            new MultiBalanceRaceSummaryResponse("PPT", "PTZ"),
                            new MultiBalancePenaltySummaryResponse(0, 0, 0)
                        )
                    )
                )
            );
        mockMvc
            .perform(
                post("/api/matches/balance/multi")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupId\":1,\"playerIds\":[1,2,3,4,5,6],\"balanceMode\":\"MMR_FIRST\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matches[0].homeMmr").doesNotExist())
            .andExpect(jsonPath("$.matches[0].awayMmr").doesNotExist())
            .andExpect(jsonPath("$.matches[0].mmrDiff").doesNotExist())
            .andExpect(jsonPath("$.matches[0].expectedHomeWinRate").value(0.61))
            .andExpect(jsonPath("$.matches[0].homeTeam[0].mmr").doesNotExist())
            .andExpect(jsonPath("$.matches[0].awayTeam[0].mmr").doesNotExist());
    }

    @Test
    void returnsForbiddenForPlayerMmrUpdateWithoutSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10/mmr")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mmr\":1200}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForPlayerMmrUpdateWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10/mmr")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mmr\":1200}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsPlayerMmrUpdateWithSuperAdminEmail() throws Exception {
        mockMvc
                    .perform(
                patch("/api/groups/1/players/10/mmr")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mmr\":1200}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForPlayerDeleteWithoutAdminEmail() throws Exception {
        mockMvc
            .perform(delete("/api/groups/1/players/10"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsPlayerDeleteWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                delete("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void doesNotRequireAdminKeyForHealthEndpoint() throws Exception {
        mockMvc
            .perform(get("/api/health"))
            .andExpect(status().isOk());
    }

    @Test
    void allowsRecentMatchesEndpointWithoutUserEmailHeader() throws Exception {
        when(matchQueryService.getRecentMatches(eq(1L), any())).thenReturn(List.of());
        mockMvc
            .perform(get("/api/groups/1/matches/recent"))
            .andExpect(status().isOk());
    }
}
