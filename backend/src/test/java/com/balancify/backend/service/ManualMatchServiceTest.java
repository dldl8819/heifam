package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.ManualMatchCreateRequest;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualMatchServiceTest {

    @Mock
    private GroupMatchAdminService groupMatchAdminService;

    @Mock
    private MatchResultService matchResultService;

    private ManualMatchService manualMatchService;

    @BeforeEach
    void setUp() {
        manualMatchService = new ManualMatchService(groupMatchAdminService, matchResultService);
    }

    @Test
    void createsManualMatchAndProcessesResult() {
        Match match = new Match();
        match.setId(500L);
        when(groupMatchAdminService.createConfirmedMatch(
            eq(1L),
            eq(List.of(1L, 2L, 3L)),
            eq(List.of(4L, 5L, 6L)),
            eq(3),
            eq(MatchSource.MANUAL),
            eq("리겜 수동 입력"),
            eq("PPT"),
            eq(false)
        )).thenReturn(match);
        when(matchResultService.processMatchResult(
            eq(500L),
            any(MatchResultRequest.class),
            eq("admin@hei.gg"),
            eq("운영진"),
            eq(false)
        )).thenReturn(new MatchResultResponse(500L, "HOME", 32, 0.5, 0.5, List.of()));

        MatchResultResponse response = manualMatchService.createManualMatch(
            new ManualMatchCreateRequest(
                1L,
                3,
                List.of(1L, 2L, 3L),
                List.of(4L, 5L, 6L),
                "HOME",
                "리겜 수동 입력",
                "PPT"
            ),
            "admin@hei.gg",
            "운영진"
        );

        assertThat(response.matchId()).isEqualTo(500L);
        verify(matchResultService).processMatchResult(
            eq(500L),
            any(MatchResultRequest.class),
            eq("admin@hei.gg"),
            eq("운영진"),
            eq(false)
        );
    }

    @Test
    void rejectsMissingGroupId() {
        assertThatThrownBy(() -> manualMatchService.createManualMatch(
            new ManualMatchCreateRequest(null, 3, List.of(1L, 2L, 3L), List.of(4L, 5L, 6L), "HOME", null),
            "admin@hei.gg",
            "운영진"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("groupId is required");
    }

    @Test
    void rejectsMissingWinnerTeam() {
        assertThatThrownBy(() -> manualMatchService.createManualMatch(
            new ManualMatchCreateRequest(1L, 3, List.of(1L, 2L, 3L), List.of(4L, 5L, 6L), " ", null),
            "admin@hei.gg",
            "운영진"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("winnerTeam must be HOME or AWAY");
    }

    @Test
    void rejectsMissingRaceComposition() {
        assertThatThrownBy(() -> manualMatchService.createManualMatch(
            new ManualMatchCreateRequest(1L, 3, List.of(1L, 2L, 3L), List.of(4L, 5L, 6L), "HOME", null),
            "admin@hei.gg",
            "운영진"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("종족 조합을 선택해 주세요.");
    }
}
