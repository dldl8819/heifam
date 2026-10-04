package com.balancify.backend.service;

import com.balancify.backend.api.points.dto.PointHistoryItemResponse;
import com.balancify.backend.api.points.dto.PointMonthlyHistoryResponse;
import com.balancify.backend.api.points.dto.PointReasonTotalResponse;
import com.balancify.backend.api.points.dto.PointRankingEntryResponse;
import com.balancify.backend.api.points.dto.PointRankingResponse;
import com.balancify.backend.api.points.dto.PointSummaryResponse;
import com.balancify.backend.config.PointProperties;
import com.balancify.backend.domain.PointAccount;
import com.balancify.backend.domain.PointTransaction;
import com.balancify.backend.repository.PointAccountRepository;
import com.balancify.backend.repository.PointTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Points members earn for activity. Every change is a new row in an append-only ledger, and
 * each grant first locks the person's account row, so daily caps hold under concurrent requests.
 */
@Service
public class PointService {

    public static final String REASON_DAILY_LOGIN = "DAILY_LOGIN";
    public static final String REASON_MATCH_RESULT = "MATCH_RESULT";
    public static final String REASON_MATCH_RESULT_REVERSED = "MATCH_RESULT_REVERSED";
    public static final String REASON_ADJUSTMENT = "ADJUSTMENT";
    public static final String REASON_PREDICTION_HIT = "PREDICTION_HIT";
    public static final String REASON_PREDICTION_HIT_REVERSED = "PREDICTION_HIT_REVERSED";
    public static final String REASON_NOTICE_READ = "NOTICE_READ";
    public static final String REASON_NOTICE_LIKE = "NOTICE_LIKE";
    public static final String REASON_NOTICE_COMMENT = "NOTICE_COMMENT";
    private static final List<String> NOTICE_REASONS = List.of(REASON_NOTICE_READ, REASON_NOTICE_LIKE, REASON_NOTICE_COMMENT);
    private static final List<String> PREDICTION_REASONS = List.of(REASON_PREDICTION_HIT, REASON_PREDICTION_HIT_REVERSED);

    private static final int RANKING_LIMIT = 50;
    private static final int MONTHLY_HISTORY_LIMIT = 200;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PointAccountRepository pointAccountRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final AccessControlService accessControlService;
    private final PointProperties pointProperties;
    private final Clock clock;
    // /api/access/me runs on every page load; this keeps it from touching the ledger once a day is done.
    private final ConcurrentMap<String, LocalDate> dailyLoginGrantedOn = new ConcurrentHashMap<>();

    @Autowired
    public PointService(
        PointAccountRepository pointAccountRepository,
        PointTransactionRepository pointTransactionRepository,
        AccessControlService accessControlService,
        PointProperties pointProperties
    ) {
        this(
            pointAccountRepository,
            pointTransactionRepository,
            accessControlService,
            pointProperties,
            Clock.system(KST)
        );
    }

    PointService(
        PointAccountRepository pointAccountRepository,
        PointTransactionRepository pointTransactionRepository,
        AccessControlService accessControlService,
        PointProperties pointProperties,
        Clock clock
    ) {
        this.pointAccountRepository = pointAccountRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.accessControlService = accessControlService;
        this.pointProperties = pointProperties;
        this.clock = clock;
    }

    /** Until points open to members, only admins earn and see them. */
    public boolean canUsePoints(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return false;
        }
        return pointProperties.isMembersEnabled()
            ? accessControlService.isServiceAccessAllowed(normalizedEmail)
            : accessControlService.isAdminEmail(normalizedEmail);
    }

    public boolean needsDailyLoginPoint(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty()
            && !today().equals(dailyLoginGrantedOn.get(normalizedEmail))
            && canUsePoints(normalizedEmail);
    }

    @Transactional
    public void grantDailyLoginPoint(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!canUsePoints(normalizedEmail)) {
            return;
        }

        LocalDate today = today();
        String referenceKey = today.toString();
        PointAccount account = lockAccount(normalizedEmail);
        if (!pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            account.getId(), REASON_DAILY_LOGIN, referenceKey
        )) {
            record(account, REASON_DAILY_LOGIN, pointProperties.getDailyLogin(), referenceKey, today, null, null);
        }
        rememberDailyLoginAfterCommit(normalizedEmail, today);
    }

    /** A first result entry for a balanced 3v3 match; the recorder earns a point, up to a daily cap. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantMatchResultPoint(String recorderEmail, Long matchId) {
        String normalizedEmail = normalizeEmail(recorderEmail);
        if (matchId == null || !canUsePoints(normalizedEmail)) {
            return;
        }

        LocalDate today = today();
        String referenceKey = matchReference(matchId);
        PointAccount account = lockAccount(normalizedEmail);
        if (pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            account.getId(), REASON_MATCH_RESULT, referenceKey
        )) {
            return;
        }
        long earnedToday = pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(
            account.getId(), REASON_MATCH_RESULT, today
        );
        if (earnedToday >= pointProperties.getMatchResultDailyCap()) {
            return;
        }
        record(account, REASON_MATCH_RESULT, pointProperties.getMatchResult(), referenceKey, today, null, null);
    }

    /** Takes back the result-entry point of a deleted match; the original row stays as history. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseMatchResultPoints(Long matchId) {
        if (matchId == null) {
            return;
        }

        String referenceKey = matchReference(matchId);
        for (PointTransaction grant : pointTransactionRepository.findByReasonAndReferenceKey(REASON_MATCH_RESULT, referenceKey)) {
            PointAccount account = lockAccount(grant.getAccount().getNormalizedEmail());
            if (pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
                account.getId(), REASON_MATCH_RESULT_REVERSED, referenceKey
            )) {
                continue;
            }
            record(account, REASON_MATCH_RESULT_REVERSED, -grant.getAmount(), referenceKey, today(), null, null);
        }
    }

    /**
     * Reading, liking and commenting on a notice each earn a point once per notice, so a notice is
     * worth up to three. Unliking or deleting the comment keeps the point and earns no second one.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantNoticePoint(String email, Long noticeId, String reason) {
        String normalizedEmail = normalizeEmail(email);
        int amount = pointProperties.getNoticeAction();
        if (noticeId == null || amount <= 0 || !NOTICE_REASONS.contains(reason) || !canUsePoints(normalizedEmail)) {
            return;
        }

        String referenceKey = "notice:" + noticeId;
        PointAccount account = lockAccount(normalizedEmail);
        if (!pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(account.getId(), reason, referenceKey)) {
            record(account, reason, amount, referenceKey, today(), null, null);
        }
    }

    /**
     * Brings one person's prediction points for a match to what the current result says: the hit
     * points when their pick won, nothing otherwise. Each change is a new ledger row, so a result
     * that flips back and forth stays exact; a new hit waits for room under the daily cap.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncPredictionPoint(String email, Long matchId, boolean hit) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty() || matchId == null) {
            return;
        }
        int target = hit && canUsePoints(normalizedEmail) ? pointProperties.getPredictionHit() : 0;
        if (target == 0 && pointAccountRepository.findByNormalizedEmail(normalizedEmail).isEmpty()) {
            return;
        }

        PointAccount account = lockAccount(normalizedEmail);
        String referencePrefix = predictionReferencePrefix(matchId);
        long current = pointTransactionRepository.sumAmountByReferencePattern(account.getId(), referencePrefix + "%");
        if (current == target) {
            return;
        }
        LocalDate today = today();
        String reason = REASON_PREDICTION_HIT_REVERSED;
        if (target > current) {
            long earnedToday = pointTransactionRepository.sumAmountByReasonsOnDate(account.getId(), PREDICTION_REASONS, today);
            if (earnedToday + (target - current) > pointProperties.getPredictionHitDailyCap()) {
                return;
            }
            reason = REASON_PREDICTION_HIT;
        }
        long sequence = pointTransactionRepository.countByAccount_IdAndReferenceKeyStartingWith(account.getId(), referencePrefix) + 1;
        record(account, reason, (int) (target - current), referencePrefix + sequence, today, null, null);
    }

    @Transactional(readOnly = true)
    public PointSummaryResponse getSummary(String email) {
        String normalizedEmail = normalizeEmail(email);
        LocalDate today = today();
        Optional<PointAccount> account = pointAccountRepository.findByNormalizedEmail(normalizedEmail);
        if (account.isEmpty()) {
            return new PointSummaryResponse(
                0L,
                false,
                pointProperties.getDailyLogin(),
                0,
                pointProperties.getMatchResultDailyCap(),
                pointProperties.getMatchResult(),
                0,
                pointProperties.getPredictionHitDailyCap(),
                pointProperties.getPredictionHit(),
                List.of()
            );
        }

        Long accountId = account.get().getId();
        List<PointHistoryItemResponse> recent = pointTransactionRepository.findTop20ByAccount_IdOrderByIdDesc(accountId)
            .stream()
            .map(transaction -> new PointHistoryItemResponse(
                transaction.getReason(),
                transaction.getAmount(),
                transaction.getKstDate(),
                transaction.getMemo(),
                transaction.getCreatedAt()
            ))
            .toList();
        return new PointSummaryResponse(
            pointTransactionRepository.sumAmountByAccountId(accountId),
            pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
                accountId, REASON_DAILY_LOGIN, today.toString()
            ),
            pointProperties.getDailyLogin(),
            (int) pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(accountId, REASON_MATCH_RESULT, today),
            pointProperties.getMatchResultDailyCap(),
            pointProperties.getMatchResult(),
            (int) pointTransactionRepository.sumAmountByReasonsOnDate(accountId, PREDICTION_REASONS, today),
            pointProperties.getPredictionHitDailyCap(),
            pointProperties.getPredictionHit(),
            recent
        );
    }

    @Transactional(readOnly = true)
    public PointRankingResponse getMonthlyRanking(YearMonth month) {
        YearMonth targetMonth = month == null ? currentMonth() : month;
        List<PointTransactionRepository.AccountPointTotal> totals = pointTransactionRepository.sumPositiveAccountTotalsBetween(
            targetMonth.atDay(1),
            targetMonth.atEndOfMonth()
        );
        List<PointTransactionRepository.AccountPointTotal> ranked = totals.subList(0, Math.min(totals.size(), RANKING_LIMIT));
        Map<String, String> nicknames = accessControlService.resolveDisplayNicknames(
            ranked.stream().map(PointTransactionRepository.AccountPointTotal::getNormalizedEmail).toList()
        );

        // Equal totals share a rank (1, 2, 2, 4). Emails stay on the server; only nicknames go out.
        List<PointRankingEntryResponse> entries = new ArrayList<>();
        long previousPoints = Long.MIN_VALUE;
        int rank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            PointTransactionRepository.AccountPointTotal total = ranked.get(index);
            long points = total.getPoints() == null ? 0L : total.getPoints();
            if (points != previousPoints) {
                rank = index + 1;
                previousPoints = points;
            }
            entries.add(new PointRankingEntryResponse(
                rank,
                total.getAccountId(),
                nicknames.get(total.getNormalizedEmail()),
                points
            ));
        }
        return new PointRankingResponse(targetMonth.toString(), entries);
    }

    /**
     * How one account earned its points in a month, as opened from the ranking: totals per reason
     * and the latest rows. Adjustment memos show only to the account itself and to super admins.
     */
    @Transactional(readOnly = true)
    public PointMonthlyHistoryResponse getMonthlyHistory(Long accountId, YearMonth month, String requesterEmail) {
        YearMonth targetMonth = month == null ? currentMonth() : month;
        PointAccount account = accountId == null ? null : pointAccountRepository.findById(accountId).orElse(null);
        if (account == null) {
            throw new NoSuchElementException("Point account not found");
        }
        List<PointTransaction> rows = pointTransactionRepository.findByAccount_IdAndKstDateBetweenOrderByIdDesc(
            accountId,
            targetMonth.atDay(1),
            targetMonth.atEndOfMonth()
        );
        String requester = normalizeEmail(requesterEmail);
        boolean showMemos = requester.equals(account.getNormalizedEmail()) || accessControlService.isSuperAdminEmail(requester);

        Map<String, long[]> byReason = new LinkedHashMap<>();
        long total = 0;
        for (PointTransaction row : rows) {
            long[] tally = byReason.computeIfAbsent(row.getReason(), ignored -> new long[2]);
            tally[0]++;
            tally[1] += row.getAmount();
            total += row.getAmount();
        }
        List<PointReasonTotalResponse> reasons = byReason.entrySet().stream()
            .map(entry -> new PointReasonTotalResponse(entry.getKey(), (int) entry.getValue()[0], entry.getValue()[1]))
            .sorted(Comparator.comparingLong(PointReasonTotalResponse::points).reversed()
                .thenComparing(Comparator.comparingInt(PointReasonTotalResponse::count).reversed())
                .thenComparing(PointReasonTotalResponse::reason))
            .toList();
        List<PointHistoryItemResponse> entries = rows.stream()
            .limit(MONTHLY_HISTORY_LIMIT)
            .map(row -> new PointHistoryItemResponse(
                row.getReason(),
                row.getAmount(),
                row.getKstDate(),
                showMemos ? row.getMemo() : null,
                row.getCreatedAt()
            ))
            .toList();
        String nickname = accessControlService.resolveDisplayNicknames(List.of(account.getNormalizedEmail()))
            .get(account.getNormalizedEmail());
        return new PointMonthlyHistoryResponse(targetMonth.toString(), accountId, nickname, total, reasons, entries);
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(KST));
    }

    private PointAccount lockAccount(String normalizedEmail) {
        pointAccountRepository.insertIfMissing(normalizedEmail);
        return pointAccountRepository.findByNormalizedEmailForUpdate(normalizedEmail)
            .orElseThrow(() -> new IllegalStateException("Point account could not be created"));
    }

    private void record(
        PointAccount account,
        String reason,
        int amount,
        String referenceKey,
        LocalDate kstDate,
        String memo,
        String createdByEmail
    ) {
        PointTransaction transaction = new PointTransaction();
        transaction.setAccount(account);
        transaction.setReason(reason);
        transaction.setAmount(amount);
        transaction.setReferenceKey(referenceKey);
        transaction.setKstDate(kstDate);
        transaction.setMemo(memo);
        transaction.setCreatedByEmail(createdByEmail);
        pointTransactionRepository.save(transaction);
    }

    // Remembered only after the commit, so a rolled-back grant is tried again on the next page load.
    private void rememberDailyLoginAfterCommit(String normalizedEmail, LocalDate day) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dailyLoginGrantedOn.put(normalizedEmail, day);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dailyLoginGrantedOn.put(normalizedEmail, day);
            }
        });
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private static String matchReference(Long matchId) {
        return "match:" + matchId;
    }

    private static String predictionReferencePrefix(Long matchId) {
        return "prediction:" + matchId + "#";
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
