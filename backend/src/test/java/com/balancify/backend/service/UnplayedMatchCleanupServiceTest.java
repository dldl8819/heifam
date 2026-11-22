package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.repository.MatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UnplayedMatchCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final OffsetDateTime A_DAY_AGO = OffsetDateTime.parse("2026-11-22T03:00:00Z");

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchResultService matchResultService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private UnplayedMatchCleanupService service() {
        return new UnplayedMatchCleanupService(
            matchRepository,
            matchResultService,
            operationAuditLogService,
            24,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void callsOffEveryMatchLeftWithoutAResultForADay() {
        when(matchRepository.findUnplayedMatchIdsSetUpBefore(eq(A_DAY_AGO), any(Pageable.class)))
            .thenReturn(List.of(11L, 12L));
        MatchResultService.DeletedMatchAuditSnapshot snapshot =
            new MatchResultService.DeletedMatchAuditSnapshot(11L, 1L, A_DAY_AGO.minusHours(5), false);
        when(matchResultService.cancelUnplayedMatchSetUpBefore(11L, A_DAY_AGO)).thenReturn(Optional.of(snapshot));
        // Its result went in after it was picked out.
        when(matchResultService.cancelUnplayedMatchSetUpBefore(12L, A_DAY_AGO)).thenReturn(Optional.empty());

        int cancelled = service().cancelMatchesLeftWithoutResult();

        assertThat(cancelled).isEqualTo(1);
        verify(operationAuditLogService).recordAutomaticMatchCancellation(snapshot, 24);
        verifyNoMoreInteractions(operationAuditLogService);
    }

    @Test
    void goesOnToTheNextMatchWhenOneCannotBeCalledOff() {
        when(matchRepository.findUnplayedMatchIdsSetUpBefore(eq(A_DAY_AGO), any(Pageable.class)))
            .thenReturn(List.of(21L, 22L));
        when(matchResultService.cancelUnplayedMatchSetUpBefore(21L, A_DAY_AGO))
            .thenThrow(new CannotAcquireLockException("deadlock detected"));
        MatchResultService.DeletedMatchAuditSnapshot snapshot =
            new MatchResultService.DeletedMatchAuditSnapshot(22L, 1L, A_DAY_AGO.minusHours(2), false);
        when(matchResultService.cancelUnplayedMatchSetUpBefore(22L, A_DAY_AGO)).thenReturn(Optional.of(snapshot));

        int cancelled = service().cancelMatchesLeftWithoutResult();

        assertThat(cancelled).isEqualTo(1);
        verify(operationAuditLogService).recordAutomaticMatchCancellation(snapshot, 24);
        verifyNoMoreInteractions(operationAuditLogService);
    }

    @Test
    void doesNothingWhenNoMatchIsLeftWaiting() {
        when(matchRepository.findUnplayedMatchIdsSetUpBefore(eq(A_DAY_AGO), any(Pageable.class)))
            .thenReturn(List.of());

        assertThat(service().cancelMatchesLeftWithoutResult()).isZero();
        verifyNoInteractions(matchResultService, operationAuditLogService);
    }
}
