package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.points.dto.PrizeCandidateResponse;
import com.balancify.backend.api.points.dto.PrizeEventConfirmRequest;
import com.balancify.backend.api.points.dto.PrizeEventCreateRequest;
import com.balancify.backend.api.points.dto.PrizeEventResponse;
import com.balancify.backend.api.points.dto.PrizeWinnerRequest;
import com.balancify.backend.api.points.dto.PrizeWinnerResponse;
import com.balancify.backend.domain.PrizeEvent;
import com.balancify.backend.domain.PrizeEventStatus;
import com.balancify.backend.domain.PrizeEventWinner;
import com.balancify.backend.repository.PointTransactionRepository;
import com.balancify.backend.repository.PrizeEventRepository;
import com.balancify.backend.repository.PrizeEventWinnerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrizeEventServiceTest {

    private static final String SUPER = "superadmin@example.com";
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Mock
    private PrizeEventRepository prizeEventRepository;

    @Mock
    private PrizeEventWinnerRepository prizeEventWinnerRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private LedgerExpenseAdminService ledgerExpenseAdminService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private PrizeEventService service;

    @BeforeEach
    void setUp() {
        service = new PrizeEventService(
            prizeEventRepository,
            prizeEventWinnerRepository,
            pointTransactionRepository,
            accessControlService,
            ledgerExpenseAdminService,
            operationAuditLogService,
            Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        when(prizeEventRepository.save(any(PrizeEvent.class))).thenAnswer(invocation -> {
            PrizeEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                ReflectionTestUtils.setField(event, "id", 5L);
            }
            return event;
        });
        when(prizeEventWinnerRepository.save(any(PrizeEventWinner.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointTransactionRepository.sumPositiveAccountTotalsBetween(START, END)).thenReturn(List.of(
            total(11L, "a@example.com", 9),
            total(12L, "b@example.com", 7),
            total(13L, "c@example.com", 7),
            total(14L, "d@example.com", 3)
        ));
        when(accessControlService.resolveDisplayNicknames(anyList())).thenReturn(Map.of(
            "a@example.com", "A",
            "b@example.com", "B",
            "c@example.com", "C",
            "d@example.com", "D"
        ));
    }

    @Test
    void opensAnEventWithTheTopEarnersAsCandidatesTiesIncluded() {
        PrizeEventResponse response = service.create(1L, new PrizeEventCreateRequest(" 10월 이벤트 ", START, END, 2), SUPER, null);

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.title()).isEqualTo("10월 이벤트");
        assertThat(response.candidates())
            .extracting(PrizeCandidateResponse::rank, PrizeCandidateResponse::pointAccountId, PrizeCandidateResponse::nickname)
            .containsExactly(tuple(1, 11L, "A"), tuple(2, 12L, "B"), tuple(2, 13L, "C"));
        verify(operationAuditLogService).recordPrizeEvent(
            eq(OperationAuditLogService.ACTION_PRIZE_EVENT_CREATED), eq(SUPER), any(), eq(5L), eq(1L), eq("10월 이벤트"), any()
        );
    }

    @Test
    void refusesEventsItCannotRun() {
        assertThatThrownBy(() -> service.create(1L, new PrizeEventCreateRequest(" ", START, END, 2), SUPER, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L, new PrizeEventCreateRequest("t", END, START, 2), SUPER, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L, new PrizeEventCreateRequest("t", START, END, 0), SUPER, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L, new PrizeEventCreateRequest("t", START, END.plusYears(2), 3), SUPER, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(prizeEventRepository, never()).save(any());
    }

    @Test
    void confirmsWinnersAndBooksEachPaidPrizeAsAnExpense() {
        PrizeEvent event = openEvent();
        when(ledgerExpenseAdminService.createEntry(eq(1L), any(), eq(SUPER), any()))
            .thenReturn(new LedgerExpenseEntryResponse(77L, null, null, null, null, 15000, null, null, null));

        PrizeEventResponse response = service.confirm(1L, 5L, new PrizeEventConfirmRequest(
            null,
            List.of(new PrizeWinnerRequest(11L, " 커피 쿠폰 ", 15000L), new PrizeWinnerRequest(13L, "응원", 0L))
        ), SUPER, "YOUR_USERNAME");

        assertThat(event.getStatus()).isEqualTo(PrizeEventStatus.CONFIRMED);
        assertThat(response.winners())
            .extracting(PrizeWinnerResponse::place, PrizeWinnerResponse::nickname, PrizeWinnerResponse::prize, PrizeWinnerResponse::ledgerLinked)
            .containsExactly(tuple(1, "A", "커피 쿠폰", true), tuple(2, "C", "응원", false));
        ArgumentCaptor<LedgerExpenseEntryCreateRequest> expense = ArgumentCaptor.forClass(LedgerExpenseEntryCreateRequest.class);
        verify(ledgerExpenseAdminService, times(1)).createEntry(eq(1L), expense.capture(), eq(SUPER), eq("YOUR_USERNAME"));
        assertThat(expense.getValue().category()).isEqualTo(PrizeEventService.LEDGER_CATEGORY);
        assertThat(expense.getValue().amount()).isEqualTo(15000L);
        assertThat(expense.getValue().entryDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(expense.getValue().target()).isNull();
        assertThat(expense.getValue().memo()).isEqualTo("10월 이벤트 1위 · 커피 쿠폰").doesNotContain("A");
        ArgumentCaptor<PrizeEventWinner> winner = ArgumentCaptor.forClass(PrizeEventWinner.class);
        verify(prizeEventWinnerRepository, times(2)).save(winner.capture());
        assertThat(winner.getAllValues().getFirst().getNormalizedEmail()).isEqualTo("a@example.com");
        assertThat(winner.getAllValues().getFirst().getLedgerExpenseId()).isEqualTo(77L);
    }

    @Test
    void acceptsOnlyCandidatesOnceEach() {
        openEvent();

        assertThatThrownBy(() -> service.confirm(1L, 5L, new PrizeEventConfirmRequest(
            null, List.of(new PrizeWinnerRequest(14L, null, 0L))
        ), SUPER, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(1L, 5L, new PrizeEventConfirmRequest(
            null, List.of(new PrizeWinnerRequest(11L, null, 0L), new PrizeWinnerRequest(11L, null, 0L))
        ), SUPER, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(1L, 5L, new PrizeEventConfirmRequest(
            null, List.of(new PrizeWinnerRequest(11L, null, -1L))
        ), SUPER, null)).isInstanceOf(IllegalArgumentException.class);
        verify(ledgerExpenseAdminService, never()).createEntry(any(), any(), any(), any());
    }

    @Test
    void paysAnEventOnlyOnceAndCancelsOnlyOpenOnes() {
        PrizeEvent event = openEvent();
        event.setStatus(PrizeEventStatus.CONFIRMED);

        assertThatThrownBy(() -> service.confirm(1L, 5L, new PrizeEventConfirmRequest(
            null, List.of(new PrizeWinnerRequest(11L, null, 0L))
        ), SUPER, null)).isInstanceOf(MatchConflictException.class);
        assertThatThrownBy(() -> service.cancel(1L, 5L, SUPER, null)).isInstanceOf(MatchConflictException.class);

        event.setStatus(PrizeEventStatus.OPEN);
        assertThat(service.cancel(1L, 5L, SUPER, null).status()).isEqualTo("CANCELLED");
    }

    private PrizeEvent openEvent() {
        PrizeEvent event = new PrizeEvent();
        ReflectionTestUtils.setField(event, "id", 5L);
        event.setGroupId(1L);
        event.setTitle("10월 이벤트");
        event.setPeriodStart(START);
        event.setPeriodEnd(END);
        event.setWinnerCount(2);
        when(prizeEventRepository.findForUpdate(5L, 1L)).thenReturn(Optional.of(event));
        return event;
    }

    private PointTransactionRepository.AccountPointTotal total(Long accountId, String email, long points) {
        return new PointTransactionRepository.AccountPointTotal() {
            @Override
            public Long getAccountId() {
                return accountId;
            }

            @Override
            public String getNormalizedEmail() {
                return email;
            }

            @Override
            public Long getPoints() {
                return points;
            }
        };
    }
}
