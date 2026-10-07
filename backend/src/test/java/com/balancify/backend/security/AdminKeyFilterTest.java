package com.balancify.backend.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.balancify.backend.api.HealthController;
import com.balancify.backend.api.access.AccessControlController;
import com.balancify.backend.api.admin.AdminRatingController;
import com.balancify.backend.api.admin.OperationAuditLogController;
import com.balancify.backend.api.admin.dto.OperationAuditLogPageResponse;
import com.balancify.backend.api.group.CaptainDraftController;
import com.balancify.backend.api.group.GroupMatchAdminController;
import com.balancify.backend.api.group.GroupMatchController;
import com.balancify.backend.api.group.GroupLedgerAdminController;
import com.balancify.backend.api.group.GroupLedgerController;
import com.balancify.backend.api.group.GroupNoticeAdminController;
import com.balancify.backend.api.group.GroupNoticeController;
import com.balancify.backend.api.group.GroupNoticeImageController;
import com.balancify.backend.api.group.dto.NoticeListResponse;
import com.balancify.backend.api.group.GroupDashboardController;
import com.balancify.backend.api.group.GroupPlayerController;
import com.balancify.backend.api.group.GroupPlayerAdminController;
import com.balancify.backend.api.group.GroupPlayerImportController;
import com.balancify.backend.api.group.GroupRankingController;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.api.group.dto.DashboardKpiSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalancePreviewResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalanceTeamPlayerResponse;
import com.balancify.backend.api.group.dto.DashboardTopRankingPreviewItemResponse;
import com.balancify.backend.api.group.dto.GroupMatchPageResponse;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerGameTypeStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerRaceStatsResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTierBoardResponse;
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
import com.balancify.backend.api.match.dto.MatchResultUpdateRequest;
import com.balancify.backend.api.match.dto.MatchResultParticipantResponse;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalancePenaltySummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRaceSummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceWaitingPlayerResponse;
import com.balancify.backend.api.points.PointController;
import com.balancify.backend.api.points.MatchConfirmationController;
import com.balancify.backend.api.points.PrizeEventController;
import com.balancify.backend.api.points.dto.PrizeEventListResponse;
import com.balancify.backend.api.points.dto.PrizeEventResponse;
import com.balancify.backend.api.prediction.PredictionController;
import com.balancify.backend.api.prediction.dto.PredictionBoardResponse;
import com.balancify.backend.api.prediction.dto.PredictionStatsResponse;
import com.balancify.backend.api.tournament.TeamScoreController;
import com.balancify.backend.api.notification.NotificationController;
import com.balancify.backend.api.notification.dto.NotificationListResponse;
import com.balancify.backend.api.series.BalanceSeriesController;
import com.balancify.backend.api.series.dto.BalanceSeriesListResponse;
import com.balancify.backend.api.tournament.TeamTournamentController;
import com.balancify.backend.api.tournament.dto.TeamScoreBoardResponse;
import com.balancify.backend.api.tournament.dto.TeamTournamentResponse;
import com.balancify.backend.api.points.dto.PointSummaryResponse;
import com.balancify.backend.api.points.dto.MatchConfirmationListResponse;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.AccountDeletionService;
import com.balancify.backend.service.CaptainDraftService;
import com.balancify.backend.service.DashboardQueryService;
import com.balancify.backend.service.GroupMatchAdminService;
import com.balancify.backend.service.LedgerExpenseAdminService;
import com.balancify.backend.service.LedgerExpenseService;
import com.balancify.backend.service.LedgerIncomeAdminService;
import com.balancify.backend.service.LedgerIncomeService;
import com.balancify.backend.service.LedgerServerCostAdminService;
import com.balancify.backend.service.LedgerServerCostService;
import com.balancify.backend.service.LedgerSummaryService;
import com.balancify.backend.service.MatchQueryService;
import com.balancify.backend.service.MatchImportService;
import com.balancify.backend.service.MatchResultService;
import com.balancify.backend.service.ManualMatchService;
import com.balancify.backend.service.MultiMatchBalancingService;
import com.balancify.backend.service.NoticeAdminService;
import com.balancify.backend.service.NoticeImageService;
import com.balancify.backend.service.NoticeService;
import com.balancify.backend.service.OperationAuditLogService;
import com.balancify.backend.service.PlayerActivityQueryService;
import com.balancify.backend.service.PlayerAdminService;
import com.balancify.backend.service.PlayerQueryService;
import com.balancify.backend.service.PlayerRaceStatsQueryService;
import com.balancify.backend.service.PlayerTeammateStatsQueryService;
import com.balancify.backend.service.PlayerImportService;
import com.balancify.backend.service.PointService;
import com.balancify.backend.service.MatchConfirmationService;
import com.balancify.backend.service.PredictionService;
import com.balancify.backend.service.PrizeEventService;
import com.balancify.backend.service.TeamScoreService;
import com.balancify.backend.service.TeamTournamentService;
import com.balancify.backend.service.BalanceSeriesService;
import com.balancify.backend.service.NotificationService;
import com.balancify.backend.service.TournamentProgressService;
import com.balancify.backend.service.exception.MatchConflictException;
import com.balancify.backend.service.exception.MatchConfirmationForbiddenException;
import com.balancify.backend.service.exception.MatchEditForbiddenException;
import com.balancify.backend.service.RankingService;
import com.balancify.backend.service.RatingRecalculationService;
import com.balancify.backend.service.TeamBalancingService;
import com.balancify.backend.service.exception.MatchEditQuotaExceededException;
import com.balancify.backend.service.exception.NoticeForbiddenException;
import com.balancify.backend.service.exception.NoticeImageException;
import com.balancify.backend.repository.NoticeImageRepository;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    MatchResultController.class,
    CaptainDraftController.class,
    AccessControlController.class,
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
    GroupMatchAdminController.class,
    OperationAuditLogController.class,
    GroupNoticeAdminController.class,
    GroupNoticeController.class,
    GroupNoticeImageController.class,
    GroupLedgerController.class,
    GroupLedgerAdminController.class,
    PointController.class,
    MatchConfirmationController.class,
    TeamTournamentController.class,
    BalanceSeriesController.class,
    NotificationController.class,
    PredictionController.class,
    TeamScoreController.class,
    PrizeEventController.class
})
@Import({ AdminKeyFilter.class, ServiceAccessFilter.class, AdminKeyProperties.class })
@TestPropertySource(properties = {
    "balancify.admin.emails=admin@hei.gg,ops@hei.gg",
    "balancify.admin.super-emails=superadmin@hei.gg",
    "balancify.auth.allow-email-header-fallback=true",
    "balancify.auth.require-jwt=false",
    "balancify.dashboard.enabled=true"
})
class AdminKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchResultService matchResultService;

    @MockitoBean
    private CaptainDraftService captainDraftService;

    @MockitoBean
    private ManualMatchService manualMatchService;

    @MockitoBean
    private MatchImportService matchImportService;

    @MockitoBean
    private PlayerImportService playerImportService;

    @MockitoBean
    private PlayerAdminService playerAdminService;

    @MockitoBean
    private GroupMatchAdminService groupMatchAdminService;

    @MockitoBean
    private MatchQueryService matchQueryService;

    @MockitoBean
    private DashboardQueryService dashboardQueryService;

    @MockitoBean
    private PlayerQueryService playerQueryService;

    @MockitoBean
    private PlayerActivityQueryService playerActivityQueryService;

    @MockitoBean
    private PlayerRaceStatsQueryService playerRaceStatsQueryService;

    @MockitoBean
    private PlayerTeammateStatsQueryService playerTeammateStatsQueryService;

    @MockitoBean
    private RankingService rankingService;

    @MockitoBean
    private TeamBalancingService teamBalancingService;

    @MockitoBean
    private MultiMatchBalancingService multiMatchBalancingService;

    @MockitoBean
    private RatingRecalculationService ratingRecalculationService;

    @MockitoBean
    private OperationAuditLogService operationAuditLogService;

    @MockitoBean
    private NoticeAdminService noticeAdminService;

    @MockitoBean
    private NoticeService noticeService;

    @MockitoBean
    private NoticeImageService noticeImageService;

    @MockitoBean
    private LedgerIncomeService ledgerIncomeService;

    @MockitoBean
    private LedgerExpenseService ledgerExpenseService;

    @MockitoBean
    private LedgerSummaryService ledgerSummaryService;

    @MockitoBean
    private LedgerIncomeAdminService ledgerIncomeAdminService;

    @MockitoBean
    private LedgerExpenseAdminService ledgerExpenseAdminService;

    @MockitoBean
    private LedgerServerCostService ledgerServerCostService;

    @MockitoBean
    private LedgerServerCostAdminService ledgerServerCostAdminService;

    @MockitoBean
    private AdminRequestResolver adminRequestResolver;

    @MockitoBean
    private SuperAdminRequestResolver superAdminRequestResolver;

    @MockitoBean
    private MmrAccessRequestResolver mmrAccessRequestResolver;

    @MockitoBean
    private AccessControlService accessControlService;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private AuthenticatedRequestResolver authenticatedRequestResolver;

    @MockitoBean
    private PointService pointService;

    @MockitoBean
    private MatchConfirmationService matchConfirmationService;

    @MockitoBean
    private TeamTournamentService teamTournamentService;

    @MockitoBean
    private TournamentProgressService tournamentProgressService;

    @MockitoBean
    private PredictionService predictionService;

    @MockitoBean
    private TeamScoreService teamScoreService;

    @MockitoBean
    private PrizeEventService prizeEventService;

    @MockitoBean
    private BalanceSeriesService balanceSeriesService;

    @MockitoBean
    private NotificationService notificationService;

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
        when(accessControlService.canViewMmr(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            String email = argument == null ? "" : argument.toString().trim().toLowerCase(Locale.ROOT);
            return superAdmins.contains(email) || "ops@hei.gg".equals(email);
        });
        when(accessControlService.resolveAccessProfile(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            String email = argument == null ? "" : argument.toString().trim().toLowerCase(Locale.ROOT);
            boolean isSuperAdmin = superAdmins.contains(email);
            boolean isAdmin = admins.contains(email) || isSuperAdmin;
            boolean isAllowed = allowed.contains(email);
            boolean canViewMmr = isSuperAdmin || "ops@hei.gg".equals(email);
            String nickname = email.contains("@") ? email.substring(0, email.indexOf('@')) : null;
            String role = isSuperAdmin ? "SUPER_ADMIN" : isAdmin ? "ADMIN" : isAllowed ? "MEMBER" : "BLOCKED";
            return new AccessControlService.AccessProfile(
                email,
                nickname,
                role,
                isAdmin,
                isSuperAdmin,
                isAllowed,
                canViewMmr,
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
        when(adminRequestResolver.isAdminRequest(any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            if (request == null) {
                return false;
            }
            String email = request.getHeader("X-USER-EMAIL");
            if (email == null || email.isBlank()) {
                return false;
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            return admins.contains(normalizedEmail) || superAdmins.contains(normalizedEmail);
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
        when(mmrAccessRequestResolver.canViewMmr(any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            if (request == null) {
                return false;
            }
            String email = request.getHeader("X-USER-EMAIL");
            if (email == null || email.isBlank()) {
                return false;
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            return superAdmins.contains(normalizedEmail) || "ops@hei.gg".equals(normalizedEmail);
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
                    false,
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
                    false,
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
    void returnsForbiddenWhenNonMemberAttemptsMatchResultPatch() throws Exception {
        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .header("X-USER-EMAIL", "stranger@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\",\"raceComposition\":\"PPT\"}")
            )
            .andExpect(status().isForbidden());

        verify(matchResultService, never()).updateMatchResult(any(), any(), any(), any());
    }

    @Test
    void allowsMemberThroughFilterForMatchResultPatchSoServiceCanDecidePerMatchOwnership() throws Exception {
        // The filter only checks group membership now; MatchResultService enforces that a
        // non-admin may only edit raceComposition on a match they personally recorded.
        MatchResultService.MatchResultUpdateAuditSnapshot auditSnapshot =
            new MatchResultService.MatchResultUpdateAuditSnapshot(
                1L,
                1L,
                "HOME",
                "HOME",
                "PPP",
                "PPT"
            );
        when(matchResultService.updateMatchResult(eq(1L), any(MatchResultUpdateRequest.class), any(), any()))
            .thenReturn(
                new MatchResultService.MatchResultUpdateOutcome(
                    new MatchResultResponse(1L, "HOME", 32, 0.5, 0.5, List.of()),
                    auditSnapshot
                )
            );

        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\",\"raceComposition\":\"PPT\"}")
            )
            .andExpect(status().isOk());

        verify(matchResultService).updateMatchResult(
            eq(1L),
            any(MatchResultUpdateRequest.class),
            eq("member@hei.gg"),
            any()
        );
    }

    @Test
    void allowsMatchResultPatchWhenAdminEmailHeaderIsValid() throws Exception {
        MatchResultService.MatchResultUpdateAuditSnapshot auditSnapshot =
            new MatchResultService.MatchResultUpdateAuditSnapshot(
                1L,
                1L,
                "HOME",
                "AWAY",
                "PPP",
                "PPT"
            );
        when(matchResultService.updateMatchResult(eq(1L), any(MatchResultUpdateRequest.class), any(), any()))
            .thenReturn(
                new MatchResultService.MatchResultUpdateOutcome(
                    new MatchResultResponse(
                        1L,
                        "AWAY",
                        32,
                        0.5,
                        0.5,
                        List.of()
                    ),
                    auditSnapshot
                )
            );
        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"AWAY\",\"raceComposition\":\"PPT\"}")
            )
            .andExpect(status().isOk());

        verify(matchResultService).updateMatchResult(
            eq(1L),
            argThat(request -> request != null
                && "AWAY".equals(request.winnerTeam())
                && "PPT".equals(request.raceComposition())),
            eq("admin@hei.gg"),
            eq("admin")
        );
    }

    @Test
    void returnsTooManyRequestsWhenEditorUsedTheDailyEditLimit() throws Exception {
        when(matchResultService.updateMatchResult(eq(1L), any(MatchResultUpdateRequest.class), any(), any()))
            .thenThrow(new MatchEditQuotaExceededException("오늘 수정할 수 있는 경기 수(10경기)를 모두 사용했습니다."));

        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"AWAY\"}")
            )
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void allowsMatchResultPatchWhenSuperAdminEmailIsValid() throws Exception {
        MatchResultService.MatchResultUpdateAuditSnapshot auditSnapshot =
            new MatchResultService.MatchResultUpdateAuditSnapshot(
                1L,
                1L,
                "HOME",
                "HOME",
                "PPP",
                "PPT"
            );
        when(matchResultService.updateMatchResult(eq(1L), any(MatchResultUpdateRequest.class), any(), any()))
            .thenReturn(
                new MatchResultService.MatchResultUpdateOutcome(
                    new MatchResultResponse(1L, "HOME", 32, 0.5, 0.5, List.of()),
                    auditSnapshot
                )
            );

        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\",\"raceComposition\":\"PPT\"}")
            )
            .andExpect(status().isOk());

        verify(matchResultService).updateMatchResult(
            eq(1L),
            argThat(request -> request != null
                && "HOME".equals(request.winnerTeam())
                && "PPT".equals(request.raceComposition())),
            eq("superadmin@hei.gg"),
            eq("superadmin")
        );
    }

    @Test
    void returnsMmrFieldsForMmrAllowedAdminMatchResult() throws Exception {
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
                    .header("X-USER-EMAIL", "ops@hei.gg")
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
    void hidesMmrFieldsFromAdminWithoutMmrAccessForMatchResult() throws Exception {
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
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeExpectedWinRate").doesNotExist())
            .andExpect(jsonPath("$.awayExpectedWinRate").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrBefore").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrAfter").doesNotExist())
            .andExpect(jsonPath("$.participants[0].mmrDelta").doesNotExist());
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
        when(playerImportService.importPlayers(eq(1L), any(), eq("ops@hei.gg"), any()))
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
    void namesWhoChangedOrImportedPlayersFromAccessControlNotFromTheToken() throws Exception {
        when(playerImportService.importPlayers(eq(1L), any(), eq("ops@hei.gg"), any()))
            .thenReturn(new GroupPlayerImportResponse(1, 1, 0, 0, List.of()));

        // The nickname header stands for the token's nickname, which its holder can set to anything.
        mockMvc
            .perform(
                post("/api/groups/1/players/import")
                    .header("X-USER-EMAIL", "ops@hei.gg")
                    .header("X-USER-NICKNAME", "superadmin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"players\":[]}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .header("X-USER-NICKNAME", "superadmin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tier\":\"B+\"}")
            )
            .andExpect(status().isOk());

        verify(playerImportService).importPlayers(eq(1L), any(), eq("ops@hei.gg"), eq("ops"));
        verify(playerAdminService).updatePlayer(eq(1L), eq(10L), any(), eq("admin@hei.gg"), eq("admin"), isNull());
    }

    @Test
    void showsAdminOnlyTheMatchResultEditorsLogs() throws Exception {
        when(operationAuditLogService.getResultEditorLogs(anyInt(), anyInt(), any()))
            .thenReturn(new OperationAuditLogPageResponse(List.of(), 0, 20, 0, 0, true, true));

        mockMvc
            .perform(
                get("/api/admin/audit-logs")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());

        verify(operationAuditLogService).getResultEditorLogs(anyInt(), anyInt(), any());
        verify(operationAuditLogService, never()).getLogs(anyInt(), anyInt());
        verify(operationAuditLogService, never()).getLogs(anyInt(), anyInt(), any());
    }

    @Test
    void returnsForbiddenForAuditLogsWithMemberEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/admin/audit-logs")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void letsOnlySuperAdminsManageMatchResultEditors() throws Exception {
        when(accessControlService.getMatchResultEditors()).thenReturn(List.of());
        when(accessControlService.addMatchResultEditor(eq("superadmin@hei.gg"), eq("member@hei.gg")))
            .thenReturn(List.of(new AccessControlService.AccessEmailEntry("member@hei.gg", "member", false)));

        mockMvc
            .perform(get("/api/access/result-editors").header("X-USER-EMAIL", "superadmin@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(
                post("/api/access/result-editors")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"member@hei.gg\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultEditors[0].email").value("member@hei.gg"));

        mockMvc
            .perform(get("/api/access/result-editors").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                post("/api/access/result-editors")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"member@hei.gg\"}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(delete("/api/access/result-editors/member@hei.gg").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isForbidden());
        verify(accessControlService, never()).removeMatchResultEditor(any(), any());
    }

    @Test
    void allowsAuditLogsWithSuperAdminEmail() throws Exception {
        when(operationAuditLogService.getLogs(anyInt(), anyInt()))
            .thenReturn(new OperationAuditLogPageResponse(List.of(), 0, 20, 0, 0, true, true));

        mockMvc
            .perform(
                get("/api/admin/audit-logs")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
            .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    void returnsForbiddenForMatchHistoryWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/matches/history")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForMatchHistoryWithoutUserEmail() throws Exception {
        mockMvc
            .perform(get("/api/groups/1/matches/history"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchHistoryWithSuperAdminEmail() throws Exception {
        when(matchQueryService.getMatchHistoryPage(eq(1L), anyInt(), any(), any(), any(), any()))
            .thenReturn(new GroupMatchPageResponse(List.of(), 0, 20, 0, 0, true, true));

        mockMvc
            .perform(
                get("/api/groups/1/matches/history")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
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
    void returnsForbiddenForAdminMmrAccessUpdateWithoutSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                put("/api/access/admins/ops%40hei.gg/mmr-access")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"canViewMmr\":true}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminMmrAccessUpdateWithSuperAdminEmail() throws Exception {
        when(accessControlService.updateManagedAdminMmrAccess(
            eq("superadmin@hei.gg"),
            eq("ops@hei.gg"),
            eq(true)
        ))
            .thenReturn(
                new AccessControlService.AdminEmailSnapshot(
                    List.of(new AccessControlService.AccessEmailEntry("superadmin@hei.gg", "superadmin", true)),
                    List.of(new AccessControlService.AccessEmailEntry("ops@hei.gg", "ops", true))
                )
            );

        mockMvc
            .perform(
                put("/api/access/admins/ops%40hei.gg/mmr-access")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"canViewMmr\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admins[0].email").value("ops@hei.gg"))
            .andExpect(jsonPath("$.admins[0].canViewMmr").value(true));

        verify(accessControlService).updateManagedAdminMmrAccess(
            eq("superadmin@hei.gg"),
            eq("ops@hei.gg"),
            eq(true)
        );
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
        when(groupMatchAdminService.createMatch(eq(1L), any(), eq("member@hei.gg")))
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
    void explainsAPlayerDeactivationTheLoginAccountBlocks() throws Exception {
        org.mockito.Mockito.doThrow(new com.balancify.backend.service.exception.AccountDeletionException(
                com.balancify.backend.service.exception.AccountDeletionException.Reason.CONFIGURED_ACCESS_LIST,
                "placeholder"
            ))
            .when(playerAdminService)
            .updatePlayer(eq(1L), eq(10L), any(), eq("admin@hei.gg"), any(), any());

        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"active\":false,\"chatLeftAt\":\"2026-10-01T00:00:00Z\",\"chatLeftReason\":\"YOUR_REASON\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ALLOWED_USER_EMAILS")));
    }

    @Test
    void allowsPlayerTierUpdateWithAdminEmailWithoutMmrAccess() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tier\":\"B+\"}")
            )
            .andExpect(status().isOk());

        verify(playerAdminService).updatePlayer(
            eq(1L),
            eq(10L),
            argThat(request -> request != null && "B+".equals(request.tier())),
            eq("admin@hei.gg"),
            eq("admin"),
            isNull()
        );
    }

    @Test
    void returnsForbiddenForTierChangeAcknowledgementWithoutMmrAccess() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tierChangeAcknowledgedTier\":\"A+\"}")
            )
            .andExpect(status().isForbidden());

        verify(playerAdminService, never()).updatePlayer(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allowsTierChangeAcknowledgementWithMmrAccess() throws Exception {
        mockMvc
            .perform(
                patch("/api/groups/1/players/10")
                    .header("X-USER-EMAIL", "ops@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tierChangeAcknowledgedTier\":\"A+\"}")
            )
            .andExpect(status().isOk());

        verify(playerAdminService).updatePlayer(any(), any(), any(), any(), any(), any());
    }

    @Test
    void hidesMmrFieldsFromMemberForGroupPlayers() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(false), any()))
            .thenReturn(List.of(groupPlayerResponseWithOperationalMetadata()));
        mockMvc
            .perform(
                get("/api/groups/1/players")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].baseMmr").doesNotExist())
            .andExpect(jsonPath("$[0].baseTier").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotAt").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotMmr").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotTier").doesNotExist())
            .andExpect(jsonPath("$[0].liveTier").value("B+"))
            .andExpect(jsonPath("$[0].chatLeftAt").doesNotExist())
            .andExpect(jsonPath("$[0].chatLeftReason").doesNotExist())
            .andExpect(jsonPath("$[0].chatRejoinedAt").doesNotExist())
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedTier").doesNotExist())
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedAt").doesNotExist());
    }

    @Test
    void hidesOnlyMmrFieldsFromAdminWithoutMmrAccessForGroupPlayers() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(false), any()))
            .thenReturn(List.of(groupPlayerResponseWithOperationalMetadata()));
        mockMvc
            .perform(
                get("/api/groups/1/players")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].baseMmr").doesNotExist())
            .andExpect(jsonPath("$[0].baseTier").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotAt").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotMmr").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotTier").doesNotExist())
            .andExpect(jsonPath("$[0].liveTier").value("B+"))
            .andExpect(jsonPath("$[0].chatLeftAt").exists())
            .andExpect(jsonPath("$[0].chatLeftReason").value("Synthetic note"))
            .andExpect(jsonPath("$[0].chatRejoinedAt").exists())
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedTier").value("A"))
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedAt").exists());
    }

    @Test
    void returnsMmrFieldsForMmrAllowedAdminForGroupPlayers() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(false), any()))
            .thenReturn(List.of(groupPlayerResponseWithOperationalMetadata()));
        mockMvc
            .perform(
                get("/api/groups/1/players")
                    .header("X-USER-EMAIL", "ops@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currentMmr").value(1216))
            .andExpect(jsonPath("$[0].baseMmr").value(1200))
            .andExpect(jsonPath("$[0].baseTier").value("A"))
            .andExpect(jsonPath("$[0].lastTierSnapshotAt").exists())
            .andExpect(jsonPath("$[0].lastTierSnapshotMmr").value(1200))
            .andExpect(jsonPath("$[0].lastTierSnapshotTier").value("A"))
            .andExpect(jsonPath("$[0].liveTier").value("B+"))
            .andExpect(jsonPath("$[0].chatLeftAt").exists())
            .andExpect(jsonPath("$[0].chatLeftReason").value("Synthetic note"))
            .andExpect(jsonPath("$[0].chatRejoinedAt").exists())
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedTier").value("A"))
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedAt").exists());
    }

    @Test
    void ignoresIncludeInactiveForMemberGroupPlayersRequest() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(false), any())).thenReturn(List.of());

        mockMvc
            .perform(
                get("/api/groups/1/players?includeInactive=true")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());

        verify(playerQueryService).getGroupPlayers(eq(1L), eq(false), any());
    }

    @Test
    void allowsIncludeInactiveForAdminGroupPlayersRequest() throws Exception {
        when(playerQueryService.getGroupPlayers(eq(1L), eq(true), any()))
            .thenReturn(List.of(retainedInactiveGroupPlayerResponse()));

        mockMvc
            .perform(
                get("/api/groups/1/players?includeInactive=true")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].nickname").value("RETAINED_NICKNAME"))
            .andExpect(jsonPath("$[0].active").value(false))
            .andExpect(jsonPath("$[0].race").value("T"))
            .andExpect(jsonPath("$[0].tier").doesNotExist())
            .andExpect(jsonPath("$[0].baseMmr").doesNotExist())
            .andExpect(jsonPath("$[0].baseTier").doesNotExist())
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotAt").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotMmr").doesNotExist())
            .andExpect(jsonPath("$[0].lastTierSnapshotTier").doesNotExist())
            .andExpect(jsonPath("$[0].liveTier").doesNotExist())
            .andExpect(jsonPath("$[0].wins").value(2))
            .andExpect(jsonPath("$[0].losses").value(1))
            .andExpect(jsonPath("$[0].games").value(3))
            .andExpect(jsonPath("$[0].chatLeftAt").exists())
            .andExpect(jsonPath("$[0].chatLeftReason").value("운영 비활성"))
            .andExpect(jsonPath("$[0].chatRejoinedAt").doesNotExist())
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedTier").doesNotExist())
            .andExpect(jsonPath("$[0].tierChangeAcknowledgedAt").doesNotExist())
            .andExpect(jsonPath("$[0].lifecycleStatus").value("INACTIVE"))
            .andExpect(jsonPath("$[0].identityRetainedUntil").exists());

        verify(playerQueryService).getGroupPlayers(eq(1L), eq(true), any());
    }

    @Test
    void returnsTierBoardForAdminWithoutLoadingFullPlayerStats() throws Exception {
        when(playerQueryService.getGroupPlayerTierBoard(eq(1L)))
            .thenReturn(List.of(new GroupPlayerTierBoardResponse(1L, "alpha", "P", "A", "A+", true)));

        mockMvc
            .perform(
                get("/api/groups/1/players/tier-board")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].nickname").value("alpha"))
            .andExpect(jsonPath("$[0].tier").value("A"))
            .andExpect(jsonPath("$[0].liveTier").value("A+"))
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].wins").doesNotExist());

        verify(playerQueryService).getGroupPlayerTierBoard(eq(1L));
        verify(playerQueryService, never()).getGroupPlayers(eq(1L), anyBoolean());
    }

    @Test
    void rejectsTierBoardForMember() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/players/tier-board")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());

        verify(playerQueryService, never()).getGroupPlayerTierBoard(any());
    }

    @Test
    void returnsSinglePlayerRaceStatsForAllowedMember() throws Exception {
        when(playerRaceStatsQueryService.getGroupPlayerRaceStats(eq(1L), eq(2L)))
            .thenReturn(new GroupPlayerRaceStatsResponse(
                2L,
                "alpha",
                "P",
                3,
                1,
                4,
                75.0,
                List.of(new GroupPlayerRaceStatResponse("P", 3, 1, 4, 75.0)),
                List.of(new GroupPlayerGameTypeStatResponse("PPP", 2, 1, 3, 66.67))
            ));

        mockMvc
            .perform(
                get("/api/groups/1/players/2/race-stats")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playerId").value(2))
            .andExpect(jsonPath("$.nickname").value("alpha"))
            .andExpect(jsonPath("$.byGameType[0].gameType").value("PPP"));

        verify(playerRaceStatsQueryService).getGroupPlayerRaceStats(eq(1L), eq(2L));
    }

    @Test
    void rejectsSinglePlayerRaceStatsForBlockedRequester() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/players/2/race-stats")
                    .header("X-USER-EMAIL", "blocked@hei.gg")
            )
            .andExpect(status().isForbidden());

        verify(playerRaceStatsQueryService, never()).getGroupPlayerRaceStats(any(), any());
    }

    @Test
    void returnsMaskedRankingForMember() throws Exception {
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
                        16,
                        true
                    )
                )
            );
        mockMvc
            .perform(
                get("/api/groups/1/ranking")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].nickname").value("alpha"))
            .andExpect(jsonPath("$[0].tier").value("A+"))
            .andExpect(jsonPath("$[0].wins").value(2))
            .andExpect(jsonPath("$[0].isNew").value(true))
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].mmrDelta").doesNotExist());
    }

    @Test
    void returnsMaskedRankingForAdminWithoutMmrAccess() throws Exception {
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
                        16,
                        true
                    )
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/ranking")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].nickname").value("alpha"))
            .andExpect(jsonPath("$[0].currentMmr").doesNotExist())
            .andExpect(jsonPath("$[0].mmrDelta").doesNotExist());
    }

    @Test
    void returnsRankingMmrForAdminWithMmrAccess() throws Exception {
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
                        16,
                        true
                    )
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/ranking")
                    .header("X-USER-EMAIL", "ops@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].nickname").value("alpha"))
            .andExpect(jsonPath("$[0].currentMmr").value(1216))
            .andExpect(jsonPath("$[0].mmrDelta").value(16));
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
                        16,
                        true
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
                    false,
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
    void returnsMaskedDashboardForAdminWithoutMmrAccess() throws Exception {
        when(dashboardQueryService.getGroupDashboard(eq(1L), eq("admin")))
            .thenReturn(
                new GroupDashboardResponse(
                    24,
                    new DashboardKpiSummaryResponse(10, 1400, 1320.5, 12),
                    List.of(new DashboardTopRankingPreviewItemResponse(1, "alpha", "P", 1400, 75.0)),
                    new DashboardRecentBalancePreviewResponse(
                        7L,
                        List.of(new DashboardRecentBalanceTeamPlayerResponse("alpha", 1400)),
                        List.of(new DashboardRecentBalanceTeamPlayerResponse("bravo", 1320)),
                        1400,
                        1320,
                        80,
                        OffsetDateTime.parse("2026-05-31T12:00:00Z")
                    ),
                    null,
                    null,
                    null
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/dashboard")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .header("X-USER-NICKNAME", "admin")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpiSummary.totalPlayers").value(10))
            .andExpect(jsonPath("$.kpiSummary.topMmr").value(0))
            .andExpect(jsonPath("$.kpiSummary.averageMmr").value(0.0))
            .andExpect(jsonPath("$.topRankingPreview[0].currentMmr").value(0))
            .andExpect(jsonPath("$.recentBalancePreview.homeMmr").value(0))
            .andExpect(jsonPath("$.recentBalancePreview.awayMmr").value(0))
            .andExpect(jsonPath("$.recentBalancePreview.mmrDiff").value(0));

        verify(dashboardQueryService).getGroupDashboard(eq(1L), eq("admin"));
    }

    @Test
    void returnsDashboardMmrFieldsForMmrAllowedAdmin() throws Exception {
        when(dashboardQueryService.getGroupDashboard(eq(1L), eq("ops")))
            .thenReturn(
                new GroupDashboardResponse(
                    24,
                    new DashboardKpiSummaryResponse(10, 1400, 1320.5, 12),
                    List.of(new DashboardTopRankingPreviewItemResponse(1, "alpha", "P", 1400, 75.0)),
                    null,
                    null,
                    null,
                    null
                )
            );

        mockMvc
            .perform(
                get("/api/groups/1/dashboard")
                    .header("X-USER-EMAIL", "ops@hei.gg")
                    .header("X-USER-NICKNAME", "ops")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpiSummary.topMmr").value(1400))
            .andExpect(jsonPath("$.topRankingPreview[0].currentMmr").value(1400));
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
    void returnsMmrFieldsForMmrAllowedAdminForRecentMatches() throws Exception {
        when(matchQueryService.getRecentMatches(eq(1L), any(), any(), any()))
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
                        List.of(new GroupRecentMatchPlayerResponse(10L, "alpha", "HOME", 1200, "T")),
                        List.of(new GroupRecentMatchPlayerResponse(20L, "bravo", "AWAY", 1184, "Z")),
                        3600,
                        3552,
                        48,
                        false,
                        false
                    )
                )
            );
        mockMvc
            .perform(
                get("/api/groups/1/matches/recent")
                    .header("X-USER-EMAIL", "ops@hei.gg")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].homeMmr").value(3600))
            .andExpect(jsonPath("$[0].awayMmr").value(3552))
            .andExpect(jsonPath("$[0].mmrDiff").value(48))
            .andExpect(jsonPath("$[0].homeTeam[0].mmr").value(1200))
            .andExpect(jsonPath("$[0].awayTeam[0].mmr").value(1184));
    }

    @Test
    void hidesMmrFieldsFromAdminWithoutMmrAccessForRecentMatches() throws Exception {
        when(matchQueryService.getRecentMatches(eq(1L), any(), any(), any()))
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
                        List.of(new GroupRecentMatchPlayerResponse(10L, "alpha", "HOME", 1200, "T")),
                        List.of(new GroupRecentMatchPlayerResponse(20L, "bravo", "AWAY", 1184, "Z")),
                        3600,
                        3552,
                        48,
                        false,
                        true
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
            .andExpect(jsonPath("$[0].awayTeam[0].mmr").doesNotExist())
            .andExpect(jsonPath("$[0].homeTeam[0].assignedRace").value("T"))
            .andExpect(jsonPath("$[0].awayTeam[0].assignedRace").value("Z"))
            .andExpect(jsonPath("$[0].racesRecorded").value(true));
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
    void hidesMmrFieldsFromAdminWithoutMmrAccessForBalanceResponse() throws Exception {
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
                    .header("X-USER-EMAIL", "admin@hei.gg")
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
    void returnsMmrFieldsForMmrAllowedAdminForBalanceResponse() throws Exception {
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
                    .header("X-USER-EMAIL", "ops@hei.gg")
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
                            new MultiBalancePenaltySummaryResponse(0, 0, 0),
                            null
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
    void hidesMmrFieldsFromAdminWithoutMmrAccessForMultiBalanceResponse() throws Exception {
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
                            new MultiBalancePenaltySummaryResponse(0, 0, 0),
                            null
                        )
                    )
                )
            );
        mockMvc
            .perform(
                post("/api/matches/balance/multi")
                    .header("X-USER-EMAIL", "admin@hei.gg")
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
    void returnsMmrFieldsForMmrAllowedAdminForMultiBalanceResponse() throws Exception {
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
                            new MultiBalancePenaltySummaryResponse(0, 0, 0),
                            null
                        )
                    )
                )
            );
        mockMvc
            .perform(
                post("/api/matches/balance/multi")
                    .header("X-USER-EMAIL", "ops@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupId\":1,\"playerIds\":[1,2,3,4,5,6],\"balanceMode\":\"MMR_FIRST\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matches[0].homeMmr").value(3570))
            .andExpect(jsonPath("$.matches[0].awayMmr").value(3480))
            .andExpect(jsonPath("$.matches[0].mmrDiff").value(90))
            .andExpect(jsonPath("$.matches[0].expectedHomeWinRate").value(0.61))
            .andExpect(jsonPath("$.matches[0].homeTeam[0].mmr").value(1200))
            .andExpect(jsonPath("$.matches[0].awayTeam[0].mmr").value(1170));
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

    // The route itself is open to members now, so this is the controller turning down a row that
    // belongs to someone else.
    @Test
    void returnsForbiddenForAnotherPlayersTeammateStatsWithMemberEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/players/10/teammate-stats")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsTeammateStatsWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/players/10/teammate-stats")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void allowsTeammateStatsWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/players/10/teammate-stats")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void allowsServerCostsWithMemberEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/server-costs")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void allowsServerCostsWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/server-costs")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForServerCostCreateWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/ledger/server-costs")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"serviceName\":\"Render\",\"billingMonth\":\"2026-08\",\"chargedDate\":\"2026-09-01\",\"usdAmount\":7.41}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void opensPointsToMembersButNotToAccountsWithoutAccess() throws Exception {
        when(pointService.canUsePoints("member@hei.gg")).thenReturn(true);
        when(pointService.getMonthlyRanking(any()))
            .thenReturn(new com.balancify.backend.api.points.dto.PointRankingResponse("2026-10", List.of()));

        mockMvc
            .perform(get("/api/points/ranking").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(get("/api/points/ranking").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/points/me"))
            .andExpect(status().isForbidden());
        verify(pointService, never()).getSummary(any());
    }

    @Test
    void grantsTheDailyLoginPointWithoutHoldingUpTheAccessCheck() throws Exception {
        when(pointService.needsDailyLoginPoint("admin@hei.gg")).thenReturn(true);
        doThrow(new IllegalStateException("ledger unavailable")).when(pointService).grantDailyLoginPoint("admin@hei.gg");

        mockMvc
            .perform(get("/api/access/me").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(get("/api/access/me").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());

        verify(pointService).grantDailyLoginPoint("admin@hei.gg");
        verify(pointService, never()).grantDailyLoginPoint("member@hei.gg");
    }

    @Test
    void showsAdminsTheirPoints() throws Exception {
        when(pointService.canUsePoints("admin@hei.gg")).thenReturn(true);
        when(pointService.getSummary("admin@hei.gg"))
            .thenReturn(new PointSummaryResponse(3L, true, 1, 2, 10, 1, 0, 10, 1, 0, 10, 1, 48, 0, 10, 1, List.of()));

        mockMvc
            .perform(get("/api/points/me").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(3));
    }

    @Test
    void rejectsAMalformedRankingMonth() throws Exception {
        when(pointService.canUsePoints("admin@hei.gg")).thenReturn(true);

        mockMvc
            .perform(get("/api/points/ranking").param("month", "October").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void letsSuperAdminsRunPrizeEventsAndAdminsSeeThem() throws Exception {
        when(pointService.canUsePoints("admin@hei.gg")).thenReturn(true);
        when(prizeEventService.list(1L)).thenReturn(new PrizeEventListResponse(List.of()));
        when(prizeEventService.create(eq(1L), any(), eq("superadmin@hei.gg"), any()))
            .thenReturn(new PrizeEventResponse(5L, "10월", null, null, 3, "OPEN", null, List.of(), List.of()));
        String body = "{\"title\":\"10월\",\"periodStart\":\"2026-10-01\",\"periodEnd\":\"2026-10-31\",\"winnerCount\":3}";

        mockMvc
            .perform(get("/api/groups/1/prize-events").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/prize-events").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(post("/api/groups/1/prize-events").header("X-USER-EMAIL", "admin@hei.gg")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/prize-events/5/confirm").header("X-USER-EMAIL", "admin@hei.gg")
                .contentType(MediaType.APPLICATION_JSON).content("{\"winners\":[]}"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/prize-events").header("X-USER-EMAIL", "superadmin@hei.gg")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(5));
        verify(prizeEventService, never()).confirm(any(), any(), any(), any(), any());
    }

    @Test
    void showsTeamScoresToAdminsOnly() throws Exception {
        when(tournamentProgressService.canRunTournaments("admin@hei.gg")).thenReturn(true);
        when(teamScoreService.board(1L)).thenReturn(new TeamScoreBoardResponse(List.of()));

        mockMvc
            .perform(get("/api/groups/1/team-scores").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/team-scores").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isArray());
        verify(teamScoreService).board(1L);
    }

    @Test
    void letsMembersPredictButKeepsClosingEarlyToAdmins() throws Exception {
        when(predictionService.canPredict("member@hei.gg")).thenReturn(true);

        mockMvc
            .perform(
                put("/api/groups/1/predictions/5")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"team\":\"HOME\"}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(post("/api/groups/1/predictions/5/close").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/predictions").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());

        verify(predictionService).predict(eq(1L), eq(5L), eq("member@hei.gg"), any(), eq("HOME"));
        verify(predictionService, never()).close(any(), any(), any(), any());
    }

    @Test
    void letsAdminsPredictAndCloseEarly() throws Exception {
        when(predictionService.canPredict("admin@hei.gg")).thenReturn(true);
        when(predictionService.board(eq(1L), eq("admin@hei.gg"), any()))
            .thenReturn(new PredictionBoardResponse(null, 3, List.of(), List.of(), List.of(), new PredictionStatsResponse(0, 0)));

        mockMvc
            .perform(get("/api/groups/1/predictions").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowMinutes").value(3));
        mockMvc
            .perform(
                put("/api/groups/1/predictions/5")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"team\":\"HOME\"}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(post("/api/groups/1/predictions/5/close").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk());

        verify(predictionService).predict(eq(1L), eq(5L), eq("admin@hei.gg"), any(), eq("HOME"));
        verify(predictionService).close(eq(1L), eq(5L), eq("admin@hei.gg"), any());
    }

    @Test
    void keepsTeamTournamentsAwayFromMembers() throws Exception {
        mockMvc
            .perform(get("/api/groups/1/tournaments/latest").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                post("/api/groups/1/tournaments")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"playerIds\":[1,2,3,4,5,6]}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/tournaments/5/cancel").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());

        verify(teamTournamentService, never()).create(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void hasNoPointAdjustmentsEvenForSuperAdmins() throws Exception {
        mockMvc
            .perform(
                post("/api/admin/points/adjustments")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"member@hei.gg\",\"amount\":5}")
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void letsMembersConfirmTheirMatchResultsButNotAccountsWithoutAccess() throws Exception {
        when(pointService.canUsePoints("member@hei.gg")).thenReturn(true);
        MatchConfirmationListResponse empty = new MatchConfirmationListResponse(List.of(), 0, 10, 1, 48);
        when(matchConfirmationService.list(eq(1L), eq("member@hei.gg"), any())).thenReturn(empty);
        when(matchConfirmationService.confirm(eq(1L), eq(5L), eq("member@hei.gg"), any())).thenReturn(empty);

        mockMvc
            .perform(get("/api/groups/1/match-confirmations").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyCap").value(10));
        mockMvc
            .perform(post("/api/groups/1/match-confirmations/5").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(get("/api/groups/1/match-confirmations").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/match-confirmations/5").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/match-confirmations/5"))
            .andExpect(status().isForbidden());

        verify(matchConfirmationService).confirm(eq(1L), eq(5L), eq("member@hei.gg"), any());
        verify(matchConfirmationService, never()).confirm(any(), any(), eq("blocked@hei.gg"), any());
    }

    @Test
    void answersMatchConfirmationRefusalsWithTheirStatus() throws Exception {
        when(pointService.canUsePoints("member@hei.gg")).thenReturn(true);
        when(matchConfirmationService.confirm(eq(1L), eq(5L), eq("member@hei.gg"), any()))
            .thenThrow(new MatchConfirmationForbiddenException("x"));
        when(matchConfirmationService.confirm(eq(1L), eq(6L), eq("member@hei.gg"), any()))
            .thenThrow(new IllegalArgumentException("x"));
        when(matchConfirmationService.confirm(eq(1L), eq(7L), eq("member@hei.gg"), any()))
            .thenThrow(new MatchConflictException("x"));
        when(matchConfirmationService.confirm(eq(1L), eq(8L), eq("member@hei.gg"), any()))
            .thenThrow(new java.util.NoSuchElementException("x"));

        for (Object[] expected : new Object[][] { { 5, 403 }, { 6, 400 }, { 7, 409 }, { 8, 404 } }) {
            mockMvc
                .perform(post("/api/groups/1/match-confirmations/" + expected[0]).header("X-USER-EMAIL", "member@hei.gg"))
                .andExpect(status().is((int) expected[1]));
        }
    }

    @Test
    void keepsMatchConfirmationsClosedWhilePointsAreClosedToTheAccount() throws Exception {
        mockMvc
            .perform(get("/api/groups/1/match-confirmations").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());

        verify(matchConfirmationService, never()).list(any(), any(), any());
    }

    @Test
    void letsMembersCallOffAMatchNobodyPlayedButKeepsDeletingToAdmins() throws Exception {
        when(matchResultService.cancelUnplayedMatch(5L, "member@hei.gg"))
            .thenReturn(new MatchResultService.DeletedMatchAuditSnapshot(5L, 1L, null, false));
        when(matchResultService.cancelUnplayedMatch(6L, "member@hei.gg")).thenThrow(new MatchConflictException("x"));
        when(matchResultService.cancelUnplayedMatch(7L, "member@hei.gg"))
            .thenThrow(new java.util.NoSuchElementException("x"));
        // Someone else set this one up.
        when(matchResultService.cancelUnplayedMatch(8L, "member@hei.gg"))
            .thenThrow(new MatchEditForbiddenException("x"));

        mockMvc
            .perform(post("/api/matches/5/cancel").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(post("/api/matches/6/cancel").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isConflict());
        mockMvc
            .perform(post("/api/matches/7/cancel").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isNotFound());
        mockMvc
            .perform(post("/api/matches/8/cancel").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/matches/5/cancel").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/matches/5/cancel"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(delete("/api/matches/5").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());

        // The service is told who asks, and decides whether the match is theirs to call off.
        verify(matchResultService, org.mockito.Mockito.times(1)).cancelUnplayedMatch(5L, "member@hei.gg");
        verify(matchResultService, never()).cancelUnplayedMatch(any(), eq("blocked@hei.gg"));
        verify(notificationService).removePredictionsOpen(5L);
        verify(notificationService, never()).removePredictionsOpen(8L);
        verify(operationAuditLogService, org.mockito.Mockito.times(1)).recordMatchDeletion(eq("member@hei.gg"), any(), any());
        verify(matchResultService, never()).deleteMatch(any());
    }

    @Test
    void opensAMonthlyPointHistoryFromTheRanking() throws Exception {
        when(pointService.canUsePoints("admin@hei.gg")).thenReturn(true);
        when(pointService.getMonthlyHistory(eq(21L), any(), eq("admin@hei.gg")))
            .thenReturn(new com.balancify.backend.api.points.dto.PointMonthlyHistoryResponse(
                "2026-09", 21L, "YOUR_USERNAME", 3L, List.of(), List.of()
            ));
        when(pointService.getMonthlyHistory(eq(99L), any(), eq("admin@hei.gg")))
            .thenThrow(new java.util.NoSuchElementException("Point account not found"));

        mockMvc
            .perform(get("/api/points/ranking/21").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/points/ranking/21").param("month", "2026-09").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(3));
        mockMvc
            .perform(get("/api/points/ranking/99").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isNotFound());
    }

    @Test
    void sendsNotificationsToMembersWithAccess() throws Exception {
        when(notificationService.canUseNotifications("member@hei.gg")).thenReturn(true);
        when(notificationService.list(1L, "member@hei.gg")).thenReturn(new NotificationListResponse(List.of(), 0));
        String subscription = "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\"}";

        mockMvc
            .perform(get("/api/groups/1/notifications"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/notifications").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        verify(notificationService, never()).list(any(), eq("blocked@hei.gg"));

        mockMvc
            .perform(get("/api/groups/1/notifications").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc
            .perform(post("/api/groups/1/notifications/read").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(
                post("/api/notifications/push-subscriptions")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(subscription)
            )
            .andExpect(status().isOk());
        verify(notificationService).markAllRead(1L, "member@hei.gg");
        verify(notificationService).subscribe(eq("member@hei.gg"), any());
    }

    @Test
    void letsMembersRunMultiBalanceSeriesButOnlyAdminsCancelThem() throws Exception {
        when(balanceSeriesService.list(eq(1L), anyBoolean())).thenReturn(new BalanceSeriesListResponse(List.of()));
        when(balanceSeriesService.start(eq(1L), any(), eq("member@hei.gg"), any(), anyBoolean()))
            .thenReturn(new BalanceSeriesListResponse(List.of()));
        when(balanceSeriesService.cancel(eq(1L), eq(5L), eq("admin@hei.gg"), any(), anyBoolean()))
            .thenReturn(new BalanceSeriesListResponse(List.of()));
        String lineup = "{\"lineups\":[{\"homePlayerIds\":[1,2,3],\"awayPlayerIds\":[4,5,6]}]}";

        mockMvc
            .perform(get("/api/groups/1/balance-series").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.series").isArray());
        mockMvc
            .perform(
                post("/api/groups/1/balance-series")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(lineup)
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(get("/api/groups/1/balance-series").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/balance-series/5/cancel").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post("/api/groups/1/balance-series/5/cancel").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk());

        verify(balanceSeriesService).start(eq(1L), any(), eq("member@hei.gg"), any(), anyBoolean());
        verify(balanceSeriesService, never()).cancel(any(), any(), eq("member@hei.gg"), any(), anyBoolean());
    }

    @Test
    void letsAdminsRunTeamTournaments() throws Exception {
        when(tournamentProgressService.canRunTournaments("admin@hei.gg")).thenReturn(true);
        when(teamTournamentService.create(eq(1L), eq(List.of(1L, 2L, 3L, 4L, 5L, 6L)), eq("admin@hei.gg"), any(), anyBoolean()))
            .thenReturn(new TeamTournamentResponse(9L, "IN_PROGRESS", 2, null, null, List.of(), List.of(), List.of()));

        mockMvc
            .perform(get("/api/groups/1/tournaments/latest").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tournament").isEmpty());
        mockMvc
            .perform(
                post("/api/groups/1/tournaments")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"playerIds\":[1,2,3,4,5,6]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tournamentId").value(9));
    }

    @Test
    void answersATournamentGameMemberCannotRecordWithForbidden() throws Exception {
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any(), anyBoolean()))
            .thenThrow(new MatchEditForbiddenException("admins only"));

        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void answersADeletionATournamentStillNeedsWithConflict() throws Exception {
        when(matchResultService.deleteMatch(1L)).thenThrow(new MatchConflictException("later game recorded"));

        mockMvc
            .perform(delete("/api/matches/1").header("X-USER-EMAIL", "admin@hei.gg"))
            .andExpect(status().isConflict());
    }

    @Test
    void showsVisitorsNoticeTitlesButNothingMore() throws Exception {
        when(noticeService.listTitles(1L)).thenReturn(List.of());

        mockMvc
            .perform(get("/api/groups/1/notice-titles"))
            .andExpect(status().isOk());
        mockMvc
            .perform(get("/api/groups/1/notices"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/notices/5"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/notices").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        verify(noticeService, never()).list(any(), any());
    }

    @Test
    void letsOnlyAdminsUploadNoticeImages() throws Exception {
        byte[] image = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        when(noticeImageService.upload(1L, image, "admin@hei.gg")).thenReturn(9L);

        mockMvc
            .perform(post("/api/groups/1/notice-images").contentType(MediaType.IMAGE_PNG).content(image))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                post("/api/groups/1/notice-images")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.IMAGE_PNG)
                    .content(image)
            )
            .andExpect(status().isForbidden());
        verify(noticeImageService, never()).upload(any(), any(), any());

        mockMvc
            .perform(
                post("/api/groups/1/notice-images")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.IMAGE_PNG)
                    .content(image)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void refusesANoticeImageTheServiceWillNotKeep() throws Exception {
        byte[] oversized = new byte[NoticeImageService.MAX_IMAGE_BYTES + 1];
        when(noticeImageService.upload(any(), any(), any()))
            .thenThrow(new NoticeImageException(NoticeImageException.Reason.UNSUPPORTED, "unsupported"))
            .thenThrow(new NoticeImageException(NoticeImageException.Reason.TOO_MANY_WAITING, "waiting"));

        mockMvc
            .perform(
                post("/api/groups/1/notice-images")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("not an image")
            )
            .andExpect(status().isUnsupportedMediaType());
        mockMvc
            .perform(
                post("/api/groups/1/notice-images")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.IMAGE_PNG)
                    .content(new byte[] {1})
            )
            .andExpect(status().isConflict());
        // Refused by its declared length, before the body is read or the service is asked.
        mockMvc
            .perform(
                post("/api/groups/1/notice-images")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.IMAGE_PNG)
                    .content(oversized)
            )
            .andExpect(status().is(413));
        verify(noticeImageService, times(2)).upload(any(), any(), any());
    }

    @Test
    void servesANoticeImageToMembersAsTheKindItWasKeptAs() throws Exception {
        byte[] image = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        when(noticeImageService.read(1L, 9L, "member@hei.gg"))
            .thenReturn(new NoticeImageRepository.StoredImage("image/jpeg", image));
        when(noticeImageService.read(1L, 10L, "member@hei.gg"))
            .thenThrow(new NoSuchElementException("Notice image not found"));

        mockMvc
            .perform(get("/api/groups/1/notice-images/9"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(get("/api/groups/1/notice-images/9").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        verify(noticeImageService, never()).read(any(), any(), any());

        mockMvc
            .perform(get("/api/groups/1/notice-images/9").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
            .andExpect(content().bytes(image));
        // Asked for as JSON, it is still the image.
        mockMvc
            .perform(
                get("/api/groups/1/notice-images/9")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(content().bytes(image));
        mockMvc
            .perform(get("/api/groups/1/notice-images/10").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isNotFound());
    }

    @Test
    void tellsThePageWhenANoticeNamesAnImageItCannotShow() throws Exception {
        when(noticeAdminService.createNotice(any(), any(), any(), any()))
            .thenThrow(new NoticeImageException(NoticeImageException.Reason.UNAVAILABLE, "unavailable"));
        when(noticeAdminService.updateNotice(any(), any(), any(), any(), any()))
            .thenThrow(new NoticeImageException(NoticeImageException.Reason.TOO_MANY_IN_NOTICE, "too many"));

        mockMvc
            .perform(
                post("/api/groups/1/notices")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"t\",\"content\":\"[[image:12]]\"}")
            )
            .andExpect(status().isConflict());
        mockMvc
            .perform(
                put("/api/groups/1/notices/5")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"t\",\"content\":\"c\"}")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void letsMembersAnswerEditAndLikeComments() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/notices/5/comments")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"a reply\",\"parentId\":9}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                put("/api/groups/1/notices/5/comments/9")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"reworded\"}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(put("/api/groups/1/notices/5/comments/9/like").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(delete("/api/groups/1/notices/5/comments/9/like").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());

        verify(noticeService).addComment(1L, 5L, "member@hei.gg", "a reply", 9L);
        verify(noticeService).editComment(1L, 5L, 9L, "member@hei.gg", "reworded");
        verify(noticeService).setCommentLike(1L, 5L, 9L, "member@hei.gg", true);
        verify(noticeService).setCommentLike(1L, 5L, 9L, "member@hei.gg", false);
    }

    @Test
    void keepsVisitorsAndBlockedAccountsOffComments() throws Exception {
        mockMvc
            .perform(
                put("/api/groups/1/notices/5/comments/9")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"reworded\"}")
            )
            .andExpect(status().isUnauthorized());
        mockMvc
            .perform(put("/api/groups/1/notices/5/comments/9/like"))
            .andExpect(status().isUnauthorized());
        mockMvc
            .perform(put("/api/groups/1/notices/5/comments/9/like").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(delete("/api/groups/1/notices/5/comments/9/like").header("X-USER-EMAIL", "blocked@hei.gg"))
            .andExpect(status().isForbidden());

        verify(noticeService, never()).editComment(any(), any(), any(), any(), any());
        verify(noticeService, never()).setCommentLike(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void tellsWhyACommentCouldNotBeChangedOrLiked() throws Exception {
        when(noticeService.editComment(any(), any(), any(), any(), any()))
            .thenThrow(new NoticeForbiddenException("not yours"));
        when(noticeService.setCommentLike(1L, 5L, 9L, "member@hei.gg", true))
            .thenThrow(new NoticeForbiddenException("your own"));
        when(noticeService.setCommentLike(1L, 5L, 10L, "member@hei.gg", true))
            .thenThrow(new NoSuchElementException("Comment not found"));

        mockMvc
            .perform(
                put("/api/groups/1/notices/5/comments/9")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"reworded\"}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/groups/1/notices/5/comments/9/like").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/groups/1/notices/5/comments/10/like").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isNotFound());
    }

    @Test
    void letsMembersReadLikeAndCommentOnNotices() throws Exception {
        when(noticeService.list(1L, "member@hei.gg")).thenReturn(new NoticeListResponse(List.of(), 0, 0));

        mockMvc
            .perform(get("/api/groups/1/notices").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc
            .perform(
                post("/api/groups/1/notices/5/comments")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"hello\"}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(put("/api/groups/1/notices/5/like").header("X-USER-EMAIL", "member@hei.gg"))
            .andExpect(status().isOk());
        mockMvc
            .perform(
                post("/api/groups/1/notices")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"t\",\"content\":\"c\"}")
            )
            .andExpect(status().isForbidden());

        verify(noticeService).addComment(1L, 5L, "member@hei.gg", "hello", null);
        verify(noticeService).setLike(1L, 5L, "member@hei.gg", true);
    }

    @Test
    void allowsServerCostCreateWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/ledger/server-costs")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"serviceName\":\"Render\",\"billingMonth\":\"2026-08\",\"chargedDate\":\"2026-09-01\",\"usdAmount\":7.41}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void allowsServerCostUpdateWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                put("/api/groups/1/ledger/server-costs/5")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"serviceName\":\"Render\",\"billingMonth\":\"2026-08\",\"chargedDate\":\"2026-09-01\",\"usdAmount\":7.41}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForServerCostDeleteWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                delete("/api/groups/1/ledger/server-costs/5")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsLedgerDashboardWithMemberEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/dashboard")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void keepsTheLedgerFromPeopleWithoutAccess() throws Exception {
        mockMvc
            .perform(get("/api/groups/1/ledger/dashboard"))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                get("/api/groups/1/ledger/income")
                    .header("X-USER-EMAIL", "blocked@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsLedgerDashboardWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/dashboard")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void allowsLedgerSummaryWithMemberEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/summary")
                    .param("year", "2026")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void letsMembersReadButNotChangeTheLedger() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/income")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                get("/api/groups/1/ledger/expense/categories")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                post("/api/groups/1/ledger/income")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entryDate\":\"2026-09-01\",\"category\":\"YOUR_CATEGORY\",\"amount\":10000}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                delete("/api/groups/1/ledger/expense/5")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsLedgerSummaryWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/summary")
                    .param("year", "2026")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void allowsLedgerIncomeListWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                get("/api/groups/1/ledger/income")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForLedgerIncomeCreateWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/ledger/income")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entryDate\":\"2026-09-01\",\"category\":\"후원\",\"amount\":10000,\"memo\":\"테스트\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsLedgerIncomeCreateWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/ledger/income")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entryDate\":\"2026-09-01\",\"category\":\"후원\",\"amount\":10000,\"memo\":\"테스트\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForLedgerExpenseDeleteWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                delete("/api/groups/1/ledger/expense/3")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsLedgerExpenseImportWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/ledger/expense/import")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"csvContent\":\"\",\"expenseType\":\"VARIABLE\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForNoticeCreateWithMemberEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/notices")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"제목\",\"content\":\"내용\"}")
            )
            .andExpect(status().isForbidden());

        verify(noticeAdminService, never()).createNotice(any(), any(), any(), any());
    }

    @Test
    void allowsNoticeCreateWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/notices")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"제목\",\"content\":\"내용\"}")
            )
            .andExpect(status().isOk());

        verify(noticeAdminService).createNotice(
            eq(1L),
            argThat(request -> request != null && "제목".equals(request.title())),
            eq("admin@hei.gg"),
            eq("admin")
        );
    }

    @Test
    void allowsNoticeUpdateWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                put("/api/groups/1/notices/5")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"수정한 제목\",\"content\":\"내용\"}")
            )
            .andExpect(status().isOk());

        verify(noticeAdminService).updateNotice(
            eq(1L),
            eq(5L),
            argThat(request -> request != null && request.announceAgain() == null),
            eq("admin@hei.gg"),
            eq("admin")
        );
    }

    @Test
    void passesOnTheChoiceToAnnounceANoticeEditAgain() throws Exception {
        mockMvc
            .perform(
                put("/api/groups/1/notices/5")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"수정한 제목\",\"content\":\"내용\",\"notify\":true}")
            )
            .andExpect(status().isOk());

        verify(noticeAdminService).updateNotice(
            eq(1L),
            eq(5L),
            argThat(request -> request != null && Boolean.TRUE.equals(request.announceAgain())),
            eq("admin@hei.gg"),
            eq("admin")
        );
    }

    @Test
    void returnsForbiddenForNoticeDeleteWithAdminEmail() throws Exception {
        mockMvc
            .perform(
                delete("/api/groups/1/notices/5")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isForbidden());

        verify(noticeAdminService, never()).deleteNotice(any(), any(), any(), any());
    }

    @Test
    void allowsNoticeDeleteWithSuperAdminEmail() throws Exception {
        mockMvc
            .perform(
                delete("/api/groups/1/notices/5")
                    .header("X-USER-EMAIL", "superadmin@hei.gg")
            )
            .andExpect(status().isOk());

        verify(noticeAdminService).deleteNotice(eq(1L), eq(5L), eq("superadmin@hei.gg"), eq("superadmin"));
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
        when(matchQueryService.getRecentMatches(eq(1L), any(), any(), any())).thenReturn(List.of());
        mockMvc
            .perform(get("/api/groups/1/matches/recent"))
            .andExpect(status().isOk());
    }

    private GroupPlayerResponse groupPlayerResponseWithOperationalMetadata() {
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T03:41:00Z");
        OffsetDateTime chatRejoinedAt = OffsetDateTime.parse("2026-05-03T04:42:00Z");
        OffsetDateTime snapshotAt = OffsetDateTime.parse("2026-04-30T14:59:59Z");
        return new GroupPlayerResponse(
            10L,
            "PlayerAlpha",
            "P",
            "A",
            1200,
            "A",
            1216,
            snapshotAt,
            1200,
            "A",
            "B+",
            2,
            1,
            3,
            true,
            chatLeftAt,
            "Synthetic note",
            chatRejoinedAt,
            "A",
            chatRejoinedAt,
            false
        );
    }

    private GroupPlayerResponse retainedInactiveGroupPlayerResponse() {
        return new GroupPlayerResponse(
            10L,
            "RETAINED_NICKNAME",
            "T",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            2,
            1,
            3,
            false,
            OffsetDateTime.parse("2026-07-12T03:00:00Z"),
            "운영 비활성",
            null,
            null,
            null,
            "INACTIVE",
            OffsetDateTime.parse("2031-07-12T03:00:00Z"),
            false
        );
    }

    @Test
    void refusesEncodedOrParameterizedMemberPathsWithoutSignIn() throws Exception {
        // Spring MVC decodes these to /api/groups/... and routes them to the same controllers.
        mockMvc
            .perform(get(URI.create("/api/%67roups/1/players")))
            .andExpect(status().isUnauthorized());
        mockMvc
            .perform(get(URI.create("/%61pi/groups/1/ranking")))
            .andExpect(status().isUnauthorized());
        mockMvc
            .perform(get(URI.create("/api/groups;x=1/1/players")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesEncodedMemberPathsForEmailsWithoutAccess() throws Exception {
        mockMvc
            .perform(
                get(URI.create("/api/%67roups/1/ranking"))
                    .header("X-USER-EMAIL", "blocked@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void stillServesEncodedPathsToMembers() throws Exception {
        when(rankingService.getGroupRanking(eq(1L))).thenReturn(List.of());

        mockMvc
            .perform(
                get(URI.create("/api/%67roups/1/ranking"))
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void keepsHealthAndRecentMatchesOpenWithoutSignIn() throws Exception {
        when(matchQueryService.getRecentMatches(eq(1L), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/groups/1/matches/recent")).andExpect(status().isOk());
    }

    @Test
    void appliesTheGetRuleToHeadRequests() throws Exception {
        mockMvc
            .perform(
                head("/api/groups/1/players/dormant")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void keepsCaptainDraftsToAdmins() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/captain-drafts")
                    .header("X-USER-EMAIL", "member@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                get("/api/groups/1/captain-drafts/latest")
                    .header("X-USER-EMAIL", "member@hei.gg")
            )
            .andExpect(status().isForbidden());
        mockMvc
            .perform(
                get("/api/groups/1/captain-drafts/latest")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }
}
