package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.points.dto.PrizeCandidateResponse;
import com.balancify.backend.api.points.dto.PrizeEventConfirmRequest;
import com.balancify.backend.api.points.dto.PrizeEventCreateRequest;
import com.balancify.backend.api.points.dto.PrizeEventListResponse;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prize events: a super admin names a period, the top point earners of that period are the
 * candidates, and confirming the winners records every paid prize as a donation ledger expense.
 * The expense carries the event title and place, never the winner's name.
 */
@Service
public class PrizeEventService {

    static final int MAX_WINNERS = 20;
    static final long MAX_PRIZE_AMOUNT = 10_000_000L;
    static final String LEDGER_CATEGORY = "이벤트 상품";
    private static final String LEDGER_EXPENSE_TYPE = "VARIABLE";
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_PRIZE_LENGTH = 100;
    private static final long MAX_PERIOD_DAYS = 366;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PrizeEventRepository prizeEventRepository;
    private final PrizeEventWinnerRepository prizeEventWinnerRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final AccessControlService accessControlService;
    private final LedgerExpenseAdminService ledgerExpenseAdminService;
    private final OperationAuditLogService operationAuditLogService;
    private final Clock clock;

    @Autowired
    public PrizeEventService(
        PrizeEventRepository prizeEventRepository,
        PrizeEventWinnerRepository prizeEventWinnerRepository,
        PointTransactionRepository pointTransactionRepository,
        AccessControlService accessControlService,
        LedgerExpenseAdminService ledgerExpenseAdminService,
        OperationAuditLogService operationAuditLogService
    ) {
        this(
            prizeEventRepository,
            prizeEventWinnerRepository,
            pointTransactionRepository,
            accessControlService,
            ledgerExpenseAdminService,
            operationAuditLogService,
            Clock.system(KST)
        );
    }

    PrizeEventService(
        PrizeEventRepository prizeEventRepository,
        PrizeEventWinnerRepository prizeEventWinnerRepository,
        PointTransactionRepository pointTransactionRepository,
        AccessControlService accessControlService,
        LedgerExpenseAdminService ledgerExpenseAdminService,
        OperationAuditLogService operationAuditLogService,
        Clock clock
    ) {
        this.prizeEventRepository = prizeEventRepository;
        this.prizeEventWinnerRepository = prizeEventWinnerRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.accessControlService = accessControlService;
        this.ledgerExpenseAdminService = ledgerExpenseAdminService;
        this.operationAuditLogService = operationAuditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PrizeEventListResponse list(Long groupId) {
        List<PrizeEvent> events = prizeEventRepository.findTop20ByGroupIdOrderByIdDesc(groupId);
        Map<Long, List<PrizeEventWinner>> winnersByEvent = new HashMap<>();
        if (!events.isEmpty()) {
            List<Long> eventIds = events.stream().map(PrizeEvent::getId).toList();
            for (PrizeEventWinner winner : prizeEventWinnerRepository.findByEventIdInOrderByEventIdAscPlaceAsc(eventIds)) {
                winnersByEvent.computeIfAbsent(winner.getEventId(), ignored -> new ArrayList<>()).add(winner);
            }
        }
        return new PrizeEventListResponse(events.stream()
            .map(event -> toResponse(event, winnersByEvent.getOrDefault(event.getId(), List.of())))
            .toList());
    }

    @Transactional
    public PrizeEventResponse create(
        Long groupId,
        PrizeEventCreateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("이벤트 이름은 1~" + MAX_TITLE_LENGTH + "자로 입력해 주세요.");
        }
        LocalDate start = request.periodStart();
        LocalDate end = request.periodEnd();
        if (start == null || end == null || end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("기간은 시작일부터 종료일까지 1년 안으로 정해 주세요.");
        }
        int winnerCount = request.winnerCount() == null ? 0 : request.winnerCount();
        if (winnerCount < 1 || winnerCount > MAX_WINNERS) {
            throw new IllegalArgumentException("당첨 인원은 1~" + MAX_WINNERS + "명으로 정해 주세요.");
        }

        PrizeEvent event = new PrizeEvent();
        event.setGroupId(groupId);
        event.setTitle(title);
        event.setPeriodStart(start);
        event.setPeriodEnd(end);
        event.setWinnerCount(winnerCount);
        prizeEventRepository.save(event);
        operationAuditLogService.recordPrizeEvent(
            OperationAuditLogService.ACTION_PRIZE_EVENT_CREATED,
            actorEmail,
            actorNickname,
            event.getId(),
            groupId,
            title,
            start + "~" + end + ", winners=" + winnerCount
        );
        return toResponse(event, List.of());
    }

    /**
     * Records the winners in place order. Each prize with an amount becomes a ledger expense on
     * the payout date, linked back to the winner row.
     */
    @Transactional
    public PrizeEventResponse confirm(
        Long groupId,
        Long eventId,
        PrizeEventConfirmRequest request,
        String actorEmail,
        String actorNickname
    ) {
        PrizeEvent event = prizeEventRepository.findForUpdate(eventId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Prize event not found: " + eventId));
        if (event.getStatus() != PrizeEventStatus.OPEN) {
            throw new MatchConflictException("이미 확정했거나 취소한 이벤트입니다.");
        }
        List<PrizeWinnerRequest> requested = request == null || request.winners() == null ? List.of() : request.winners();
        if (requested.isEmpty() || requested.size() > MAX_WINNERS) {
            throw new IllegalArgumentException("당첨자를 1~" + MAX_WINNERS + "명 골라 주세요.");
        }
        LocalDate paidOn = request.paidOn() == null ? LocalDate.now(clock.withZone(KST)) : request.paidOn();

        Map<Long, Candidate> candidates = new HashMap<>();
        candidates(event).forEach(candidate -> candidates.put(candidate.accountId(), candidate));
        Set<Long> seen = new HashSet<>();
        List<PrizeEventWinner> winners = new ArrayList<>();
        long total = 0;
        for (int index = 0; index < requested.size(); index++) {
            PrizeWinnerRequest winnerRequest = requested.get(index);
            Long accountId = winnerRequest == null ? null : winnerRequest.pointAccountId();
            Candidate candidate = accountId == null ? null : candidates.get(accountId);
            if (candidate == null || !seen.add(accountId)) {
                throw new IllegalArgumentException("후보 목록에 있는 사람만 한 번씩 고를 수 있습니다.");
            }
            long amount = winnerRequest.amount() == null ? 0 : winnerRequest.amount();
            if (amount < 0 || amount > MAX_PRIZE_AMOUNT) {
                throw new IllegalArgumentException("상품 금액은 0~" + MAX_PRIZE_AMOUNT + "원으로 입력해 주세요.");
            }
            String prize = winnerRequest.prize() == null ? null : winnerRequest.prize().trim();
            if (prize != null && prize.isEmpty()) {
                prize = null;
            }
            if (prize != null && prize.length() > MAX_PRIZE_LENGTH) {
                prize = prize.substring(0, MAX_PRIZE_LENGTH);
            }

            PrizeEventWinner winner = new PrizeEventWinner();
            winner.setEventId(event.getId());
            winner.setPlace(index + 1);
            winner.setNormalizedEmail(candidate.email());
            winner.setNickname(candidate.nickname());
            winner.setPoints((int) Math.min(Integer.MAX_VALUE, candidate.points()));
            winner.setPrize(prize);
            winner.setAmount(amount);
            if (amount > 0) {
                LedgerExpenseEntryResponse expense = ledgerExpenseAdminService.createEntry(
                    groupId,
                    new LedgerExpenseEntryCreateRequest(
                        paidOn,
                        LEDGER_EXPENSE_TYPE,
                        LEDGER_CATEGORY,
                        null,
                        amount,
                        event.getTitle() + " " + (index + 1) + "위" + (prize == null ? "" : " · " + prize)
                    ),
                    actorEmail,
                    actorNickname
                );
                winner.setLedgerExpenseId(expense.id());
                total += amount;
            }
            winners.add(prizeEventWinnerRepository.save(winner));
        }

        event.setStatus(PrizeEventStatus.CONFIRMED);
        event.setConfirmedAt(OffsetDateTime.now(clock));
        prizeEventRepository.save(event);
        operationAuditLogService.recordPrizeEvent(
            OperationAuditLogService.ACTION_PRIZE_EVENT_CONFIRMED,
            actorEmail,
            actorNickname,
            event.getId(),
            groupId,
            event.getTitle(),
            "winners=" + winners.size() + ", total=" + total
        );
        return toResponse(event, winners);
    }

    @Transactional
    public PrizeEventResponse cancel(Long groupId, Long eventId, String actorEmail, String actorNickname) {
        PrizeEvent event = prizeEventRepository.findForUpdate(eventId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Prize event not found: " + eventId));
        if (event.getStatus() != PrizeEventStatus.OPEN) {
            throw new MatchConflictException("확정 전 이벤트만 취소할 수 있습니다.");
        }
        event.setStatus(PrizeEventStatus.CANCELLED);
        prizeEventRepository.save(event);
        operationAuditLogService.recordPrizeEvent(
            OperationAuditLogService.ACTION_PRIZE_EVENT_CANCELLED,
            actorEmail,
            actorNickname,
            event.getId(),
            groupId,
            event.getTitle(),
            null
        );
        return toResponse(event, List.of());
    }

    // The period's top earners by net points; equal totals share a place, so ties at the cut all count.
    private List<Candidate> candidates(PrizeEvent event) {
        List<PointTransactionRepository.AccountPointTotal> totals =
            pointTransactionRepository.sumPositiveAccountTotalsBetween(event.getPeriodStart(), event.getPeriodEnd());
        List<PointTransactionRepository.AccountPointTotal> ranked = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (int index = 0; index < totals.size(); index++) {
            long points = totals.get(index).getPoints() == null ? 0 : totals.get(index).getPoints();
            if (points != previous) {
                rank = index + 1;
                previous = points;
            }
            if (rank > event.getWinnerCount()) {
                break;
            }
            ranked.add(totals.get(index));
            ranks.add(rank);
        }
        Map<String, String> nicknames = accessControlService.resolveDisplayNicknames(
            ranked.stream().map(PointTransactionRepository.AccountPointTotal::getNormalizedEmail).toList()
        );
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            PointTransactionRepository.AccountPointTotal total = ranked.get(index);
            candidates.add(new Candidate(
                ranks.get(index),
                total.getAccountId(),
                total.getNormalizedEmail(),
                nicknames.get(total.getNormalizedEmail()),
                total.getPoints() == null ? 0 : total.getPoints()
            ));
        }
        return candidates;
    }

    private PrizeEventResponse toResponse(PrizeEvent event, List<PrizeEventWinner> winners) {
        List<PrizeCandidateResponse> candidates = event.getStatus() == PrizeEventStatus.OPEN
            ? candidates(event).stream()
                .map(candidate -> new PrizeCandidateResponse(
                    candidate.rank(),
                    candidate.accountId(),
                    candidate.nickname(),
                    candidate.points()
                ))
                .toList()
            : List.of();
        return new PrizeEventResponse(
            event.getId(),
            event.getTitle(),
            event.getPeriodStart(),
            event.getPeriodEnd(),
            event.getWinnerCount(),
            event.getStatus().name(),
            event.getConfirmedAt(),
            candidates,
            winners.stream()
                .map(winner -> new PrizeWinnerResponse(
                    winner.getPlace(),
                    winner.getNickname(),
                    winner.getPoints(),
                    winner.getPrize(),
                    winner.getAmount(),
                    winner.getLedgerExpenseId() != null
                ))
                .toList()
        );
    }

    private record Candidate(int rank, Long accountId, String email, String nickname, long points) {
    }
}
