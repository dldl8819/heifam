package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.admin.dto.RatingRecalculationPlayerChangeResponse;
import com.balancify.backend.api.admin.dto.RatingRecalculationRequest;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingRecalculationServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private RatingReplayCalculator ratingReplayCalculator;

    @Mock
    private RatingRecalculationWriteService ratingRecalculationWriteService;

    private RatingRecalculationService ratingRecalculationService;

    @BeforeEach
    void setUp() {
        ratingRecalculationService = new RatingRecalculationService(
            playerRepository,
            matchRepository,
            matchParticipantRepository,
            ratingReplayCalculator,
            ratingRecalculationWriteService
        );
    }

    @Test
    void dryRunDoesNotWriteAnyChanges() {
        RatingReplayPlan plan = new RatingReplayPlan(
            List.of(),
            List.of(),
            3,
            6,
            4.5,
            List.of(new RatingRecalculationPlayerChangeResponse(1L, "민식", 700, 680))
        );

        when(playerRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findByWinningTeamIsNotNullOrderByPlayedAtAscIdAsc()).thenReturn(List.of());
        when(ratingReplayCalculator.calculate(any(), any(), any())).thenReturn(plan);

        ratingRecalculationService.recalculate(new RatingRecalculationRequest(false, true));

        verify(ratingRecalculationWriteService, never()).apply(any());
    }

    @Test
    void applyWritesWhenConfirmed() {
        RatingReplayPlan plan = new RatingReplayPlan(
            List.of(),
            List.of(),
            3,
            6,
            4.5,
            List.of()
        );

        when(playerRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findByWinningTeamIsNotNullOrderByPlayedAtAscIdAsc()).thenReturn(List.of());
        when(ratingReplayCalculator.calculate(any(), any(), any())).thenReturn(plan);

        ratingRecalculationService.recalculate(new RatingRecalculationRequest(true, false));

        verify(ratingRecalculationWriteService).apply(plan);
    }

    @Test
    void requiresConfirmationForRealWrite() {
        assertThatThrownBy(() ->
            ratingRecalculationService.recalculate(new RatingRecalculationRequest(false, false))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("confirm=true is required");
    }
}
