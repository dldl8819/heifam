package com.balancify.backend.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.balancify.backend.api.HealthController;
import com.balancify.backend.api.group.GroupMatchAdminController;
import com.balancify.backend.api.group.GroupMatchController;
import com.balancify.backend.api.group.GroupPlayerAdminController;
import com.balancify.backend.api.group.GroupPlayerImportController;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.api.group.dto.GroupPlayerImportResponse;
import com.balancify.backend.api.match.MatchImportController;
import com.balancify.backend.api.match.dto.MatchImportResponse;
import com.balancify.backend.api.match.MatchResultController;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.service.GroupMatchAdminService;
import com.balancify.backend.service.MatchQueryService;
import com.balancify.backend.service.MatchImportService;
import com.balancify.backend.service.MatchResultService;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerAdminService;
import com.balancify.backend.service.PlayerImportService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    HealthController.class,
    GroupMatchController.class,
    GroupPlayerImportController.class,
    GroupPlayerAdminController.class,
    GroupMatchAdminController.class
})
@Import({ AdminKeyFilter.class, ServiceAccessFilter.class, AdminKeyProperties.class })
@TestPropertySource(properties = {
    "balancify.admin.api-key=test-admin-key",
    "balancify.admin.emails=admin@hei.gg,ops@hei.gg",
    "balancify.admin.super-emails=minsiklee2@gmail.com"
})
class AdminKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchResultService matchResultService;

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
    private AdminRequestResolver adminRequestResolver;

    @MockBean
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        Set<String> admins = Set.of("admin@hei.gg", "ops@hei.gg");
        Set<String> superAdmins = Set.of("minsiklee2@gmail.com");
        Set<String> allowed = Set.of("admin@hei.gg", "ops@hei.gg", "minsiklee2@gmail.com");

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
    void returnsForbiddenWhenUserEmailIsNotAdminForMatchResult() throws Exception {
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
    void allowsMatchResultWhenUserEmailIsAdmin() throws Exception {
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any()))
            .thenReturn(
                new MatchResultResponse(
                    1L,
                    "HOME",
                    32,
                    0.5,
                    0.5,
                    List.of()
                )
            );

        mockMvc
            .perform(
                post("/api/matches/1/result")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"HOME\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenWhenAdminKeyHeaderIsMissingForMatchResultPatch() throws Exception {
        mockMvc
            .perform(
                patch("/api/matches/1/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"AWAY\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchResultPatchWhenAdminKeyHeaderIsValid() throws Exception {
        when(matchResultService.processMatchResult(eq(1L), any(MatchResultRequest.class), any(), any()))
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
                    .header("X-ADMIN-KEY", "test-admin-key")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"winnerTeam\":\"AWAY\"}")
            )
            .andExpect(status().isOk());
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
                    .header("X-ADMIN-KEY", "test-admin-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[]")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsForbiddenForMatchDeleteWithoutAdminKey() throws Exception {
        mockMvc
            .perform(delete("/api/matches/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMatchDeleteWithValidAdminKey() throws Exception {
        mockMvc
            .perform(
                delete("/api/matches/1")
                    .header("X-ADMIN-KEY", "test-admin-key")
                    .header("X-USER-EMAIL", "admin@hei.gg")
            )
            .andExpect(status().isOk());
    }

    @Test
    void returnsForbiddenForGroupMatchCreateWithoutAdminKey() throws Exception {
        mockMvc
            .perform(
                post("/api/groups/1/matches")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"homePlayerIds\":[1,2,3],\"awayPlayerIds\":[4,5,6]}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsGroupMatchCreateWithValidAdminKey() throws Exception {
        when(groupMatchAdminService.createMatch(eq(1L), any()))
            .thenReturn(new CreateGroupMatchResponse(100L));

        mockMvc
            .perform(
                post("/api/groups/1/matches")
                    .header("X-ADMIN-KEY", "test-admin-key")
                    .header("X-USER-EMAIL", "admin@hei.gg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"homePlayerIds\":[1,2,3],\"awayPlayerIds\":[4,5,6]}")
            )
            .andExpect(status().isOk());
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
        when(adminRequestResolver.isAdminRequest(any())).thenReturn(false);

        mockMvc
            .perform(get("/api/groups/1/matches/recent"))
            .andExpect(status().isOk());
    }
}
