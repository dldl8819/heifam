package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.points.dto.PrizeCandidateResponse;
import com.balancify.backend.api.points.dto.PrizeEventConfirmRequest;
import com.balancify.backend.api.points.dto.PrizeEventCreateRequest;
import com.balancify.backend.api.points.dto.PrizeEventEntryRequest;
import com.balancify.backend.api.points.dto.PrizeEventListResponse;
import com.balancify.backend.api.points.dto.PrizeEventResponse;
import com.balancify.backend.api.points.dto.PrizeWinnerRequest;
import com.balancify.backend.api.points.dto.PrizeWinnerResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PrizeEvent;
import com.balancify.backend.domain.PrizeEventEntry;
import com.balancify.backend.domain.PrizeEventMetric;
import com.balancify.backend.domain.PrizeEventStatus;
import com.balancify.backend.domain.PrizeEventWinner;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PointTransactionRepository;
import com.balancify.backend.repository.PrizeEventEntryRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prize events: an admin names a period and what ranks it, points earned (the candidates are point
 * accounts), finished matches played, or numbers counted outside the site that the admin enters,
 * such as chat messages (the candidates are players), and a super admin confirming
 * the winners records every paid prize as a donation ledger expense. The expense carries the event
 * title and place, never the winner's name.
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
    static final int MAX_MANUAL_ENTRIES = 100;
    private static final long MAX_MANUAL_SCORE = 1_000_000_000L;
    private static final int MAX_UNIT_LABEL_LENGTH = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PrizeEventRepository prizeEventRepository;
    private final PrizeEventWinnerRepository prizeEventWinnerRepository;
    private final PrizeEventEntryRepository prizeEventEntryRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PlayerRepository playerRepository;
    private final AccessControlService accessControlService;
    private final LedgerExpenseAdminService ledgerExpenseAdminService;
    private final OperationAuditLogService operationAuditLogService;
    private final Clock clock;

    @Autowired
    public PrizeEventService(
        PrizeEventRepository prizeEventRepository,
        PrizeEventWinnerRepository prizeEventWinnerRepository,
        PrizeEventEntryRepository prizeEventEntryRepository,
        PointTransactionRepository pointTransactionRepository,
        MatchParticipantRepository matchParticipantRepository,
        PlayerRepository playerRepository,
        AccessControlService accessControlService,
        LedgerExpenseAdminService ledgerExpenseAdminService,
        OperationAuditLogService operationAuditLogService
    ) {
        this(
            prizeEventRepository,
            prizeEventWinnerRepository,
            prizeEventEntryRepository,
            pointTransactionRepository,
            matchParticipantRepository,
            playerRepository,
            accessControlService,
            ledgerExpenseAdminService,
            operationAuditLogService,
            Clock.system(KST)
        );
    }

    PrizeEventService(
        PrizeEventRepository prizeEventRepository,
        PrizeEventWinnerRepository prizeEventWinnerRepository,
        PrizeEventEntryRepository prizeEventEntryRepository,
        PointTransactionRepository pointTransactionRepository,
        MatchParticipantRepository matchParticipantRepository,
        PlayerRepository playerRepository,
        AccessControlService accessControlService,
        LedgerExpenseAdminService ledgerExpenseAdminService,
        OperationAuditLogService operationAuditLogService,
        Clock clock
    ) {
        this.prizeEventRepository = prizeEventRepository;
        this.prizeEventWinnerRepository = prizeEventWinnerRepository;
        this.prizeEventEntryRepository = prizeEventEntryRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.playerRepository = playerRepository;
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
        PrizeEventMetric metric = resolveMetric(request.metric());
        String unitLabel = null;
        Map<Long, Long> manualScores = Map.of();
        if (metric == PrizeEventMetric.MANUAL) {
            unitLabel = request.unitLabel() == null ? "" : request.unitLabel().trim();
            if (unitLabel.isEmpty() || unitLabel.length() > MAX_UNIT_LABEL_LENGTH) {
                throw new IllegalArgumentException("직접 입력하는 숫자의 단위(예: 채팅)를 1~" + MAX_UNIT_LABEL_LENGTH + "자로 입력해 주세요.");
            }
            manualScores = manualScores(groupId, request.entries());
        }

        PrizeEvent event = new PrizeEvent();
        event.setGroupId(groupId);
        event.setTitle(title);
        event.setPeriodStart(start);
        event.setPeriodEnd(end);
        event.setWinnerCount(winnerCount);
        event.setMetric(metric);
        event.setUnitLabel(unitLabel);
        prizeEventRepository.save(event);
        for (Map.Entry<Long, Long> score : manualScores.entrySet()) {
            PrizeEventEntry entry = new PrizeEventEntry();
            entry.setEventId(event.getId());
            entry.setPlayerId(score.getKey());
            entry.setScore(score.getValue());
            prizeEventEntryRepository.save(entry);
        }
        operationAuditLogService.recordPrizeEvent(
            OperationAuditLogService.ACTION_PRIZE_EVENT_CREATED,
            actorEmail,
            actorNickname,
            event.getId(),
            groupId,
            title,
            start + "~" + end + ", winners=" + winnerCount + ", by=" + metric
                + (metric == PrizeEventMetric.MANUAL ? " (" + unitLabel + ", entries=" + manualScores.size() + ")" : "")
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

        boolean byPlayer = event.getMetric() != PrizeEventMetric.POINTS;
        Map<Long, Candidate> candidates = new HashMap<>();
        candidates(event).forEach(candidate -> candidates.put(byPlayer ? candidate.playerId() : candidate.accountId(), candidate));
        Set<Long> seen = new HashSet<>();
        List<PrizeEventWinner> winners = new ArrayList<>();
        long total = 0;
        for (int index = 0; index < requested.size(); index++) {
            PrizeWinnerRequest winnerRequest = requested.get(index);
            Long candidateId = winnerRequest == null ? null
                : byPlayer ? winnerRequest.playerId() : winnerRequest.pointAccountId();
            Candidate candidate = candidateId == null ? null : candidates.get(candidateId);
            if (candidate == null || !seen.add(candidateId)) {
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
            winner.setPlayerId(candidate.playerId());
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

    private List<Candidate> candidates(PrizeEvent event) {
        return switch (event.getMetric()) {
            case GAMES -> playerCandidates(event);
            case MANUAL -> manualCandidates(event);
            case POINTS -> accountCandidates(event);
        };
    }

    // The players and numbers the admin entered: each player once, on the group's roster, numbers 0 and up.
    private Map<Long, Long> manualScores(Long groupId, List<PrizeEventEntryRequest> entries) {
        if (entries == null || entries.isEmpty() || entries.size() > MAX_MANUAL_ENTRIES) {
            throw new IllegalArgumentException("순위에 넣을 선수와 숫자를 1~" + MAX_MANUAL_ENTRIES + "명 입력해 주세요.");
        }
        Map<Long, Long> scores = new LinkedHashMap<>();
        for (PrizeEventEntryRequest entry : entries) {
            if (entry == null || entry.playerId() == null || entry.score() == null
                || entry.score() < 0 || entry.score() > MAX_MANUAL_SCORE) {
                throw new IllegalArgumentException("선수마다 0~" + MAX_MANUAL_SCORE + " 사이의 숫자를 입력해 주세요.");
            }
            if (scores.put(entry.playerId(), entry.score()) != null) {
                throw new IllegalArgumentException("같은 선수를 두 번 넣을 수 없습니다.");
            }
        }
        long onRoster = playerRepository.findByGroup_IdAndIdIn(groupId, List.copyOf(scores.keySet()))
            .stream()
            .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player))
            .count();
        if (onRoster != scores.size()) {
            throw new IllegalArgumentException("명단에 있는 선수만 넣을 수 있습니다.");
        }
        return scores;
    }

    /** The numbers the admin entered when starting the event, ranked the same way as matches played. */
    private List<Candidate> manualCandidates(PrizeEvent event) {
        return rankPlayers(event, prizeEventEntryRepository.findByEventIdOrderByScoreDescPlayerIdAsc(event.getId())
            .stream()
            .map(entry -> new PlayerScore(entry.getPlayerId(), entry.getScore()))
            .toList());
    }

    /**
     * The players who played the most finished matches of the group in the period, Korean days from
     * the first to the last; equal counts share a place, so ties at the cut all count. A player who
     * left or was anonymized is no candidate.
     */
    private List<Candidate> playerCandidates(PrizeEvent event) {
        return rankPlayers(event, matchParticipantRepository.countFinishedGamesByPlayerBetween(
                event.getGroupId(),
                event.getPeriodStart().atStartOfDay(KST).toOffsetDateTime(),
                event.getPeriodEnd().plusDays(1).atStartOfDay(KST).toOffsetDateTime()
            )
            .stream()
            .map(count -> new PlayerScore(count.getPlayerId(), count.getGames() == null ? 0 : count.getGames()))
            .toList());
    }

    // Players by score, highest first; equal scores share a place, so ties at the cut all count.
    private List<Candidate> rankPlayers(PrizeEvent event, List<PlayerScore> scores) {
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(scores.stream().map(PlayerScore::playerId).toList())
            .forEach(player -> players.put(player.getId(), player));
        List<Candidate> candidates = new ArrayList<>();
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (PlayerScore score : scores) {
            Player player = players.get(score.playerId());
            if (PlayerIdentityPolicy.isIdentityHidden(player)) {
                continue;
            }
            if (score.score() != previous) {
                rank = candidates.size() + 1;
                previous = score.score();
            }
            if (rank > event.getWinnerCount()) {
                break;
            }
            candidates.add(new Candidate(rank, null, player.getId(), null, player.getNickname(), score.score()));
        }
        return candidates;
    }

    private record PlayerScore(Long playerId, long score) {
    }

    // The period's top earners by net points; equal totals share a place, so ties at the cut all count.
    private List<Candidate> accountCandidates(PrizeEvent event) {
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
        Map<String, String> nicknames = accessControlService.resolvePersonNames(
            ranked.stream().map(PointTransactionRepository.AccountPointTotal::getNormalizedEmail).toList()
        );
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            PointTransactionRepository.AccountPointTotal total = ranked.get(index);
            candidates.add(new Candidate(
                ranks.get(index),
                total.getAccountId(),
                null,
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
                    candidate.points(),
                    candidate.playerId()
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
                .toList(),
            event.getMetric().name(),
            event.getUnitLabel()
        );
    }

    private static PrizeEventMetric resolveMetric(String metric) {
        if (metric == null || metric.isBlank()) {
            return PrizeEventMetric.POINTS;
        }
        try {
            return PrizeEventMetric.valueOf(metric.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("순위 기준은 포인트(POINTS), 경기 수(GAMES), 직접 입력(MANUAL) 가운데서 정해 주세요.");
        }
    }

    // accountId and email for a points event; playerId for a matches-played one.
    private record Candidate(int rank, Long accountId, Long playerId, String email, String nickname, long points) {
    }
}
