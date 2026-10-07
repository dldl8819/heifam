package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private static final String ADMIN_EMAIL = "your_username@example.com";
    private static final String MEMBER_EMAIL = "member@example.com";
    // 00:30 in Korea on Oct 4 is still Oct 3 in UTC; points must follow the Korean date.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T15:30:00Z"), ZoneId.of("UTC"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @Mock
    private PointAccountRepository pointAccountRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private PointProperties pointProperties;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        pointProperties = new PointProperties();
        pointService = new PointService(
            pointAccountRepository,
            pointTransactionRepository,
            accessControlService,
            pointProperties,
            CLOCK
        );
        lenient().when(accessControlService.isAdminEmail(ADMIN_EMAIL)).thenReturn(true);
    }

    @Test
    void onlyAdminsUsePointsUntilMembersAreEnabled() {
        assertThat(pointService.canUsePoints(" YOUR_USERNAME@example.com ")).isTrue();
        assertThat(pointService.canUsePoints(MEMBER_EMAIL)).isFalse();
        assertThat(pointService.canUsePoints(" ")).isFalse();
        verify(accessControlService, never()).isServiceAccessAllowed(anyString());

        pointProperties.setMembersEnabled(true);
        when(accessControlService.isServiceAccessAllowed(MEMBER_EMAIL)).thenReturn(true);

        assertThat(pointService.canUsePoints(MEMBER_EMAIL)).isTrue();
    }

    @Test
    void grantsTheDailyLoginPointOncePerKoreanDay() {
        PointAccount account = stubAccount(ADMIN_EMAIL, 7L);
        assertThat(pointService.needsDailyLoginPoint(ADMIN_EMAIL)).isTrue();

        pointService.grantDailyLoginPoint(ADMIN_EMAIL);

        PointTransaction saved = captureSavedTransaction();
        assertThat(saved.getAccount()).isSameAs(account);
        assertThat(saved.getReason()).isEqualTo(PointService.REASON_DAILY_LOGIN);
        assertThat(saved.getAmount()).isEqualTo(1);
        assertThat(saved.getReferenceKey()).isEqualTo("2026-10-04");
        assertThat(saved.getKstDate()).isEqualTo(TODAY);
        assertThat(pointService.needsDailyLoginPoint(ADMIN_EMAIL)).isFalse();
    }

    @Test
    void skipsTheDailyLoginPointWhenTodaysIsAlreadyRecorded() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            7L, PointService.REASON_DAILY_LOGIN, "2026-10-04"
        )).thenReturn(true);

        pointService.grantDailyLoginPoint(ADMIN_EMAIL);

        verify(pointTransactionRepository, never()).save(any());
        assertThat(pointService.needsDailyLoginPoint(ADMIN_EMAIL)).isFalse();
    }

    @Test
    void membersEarnNothingWhilePointsAreAdminOnly() {
        assertThat(pointService.needsDailyLoginPoint(MEMBER_EMAIL)).isFalse();

        pointService.grantDailyLoginPoint(MEMBER_EMAIL);

        verifyNoInteractions(pointAccountRepository, pointTransactionRepository);
    }

    @Test
    void grantsAResultPointPerMatch() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_MATCH_RESULT, TODAY))
            .thenReturn(9L);

        pointService.grantMatchResultPoint(ADMIN_EMAIL, 5L);

        PointTransaction saved = captureSavedTransaction();
        assertThat(saved.getReason()).isEqualTo(PointService.REASON_MATCH_RESULT);
        assertThat(saved.getAmount()).isEqualTo(1);
        assertThat(saved.getReferenceKey()).isEqualTo("match:5");
        assertThat(saved.getKstDate()).isEqualTo(TODAY);
    }

    @Test
    void stopsResultPointsAtTheDailyCap() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_MATCH_RESULT, TODAY))
            .thenReturn(10L);

        pointService.grantMatchResultPoint(ADMIN_EMAIL, 5L);

        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void neverPaysTwiceForTheSameMatch() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            7L, PointService.REASON_MATCH_RESULT, "match:5"
        )).thenReturn(true);

        pointService.grantMatchResultPoint(ADMIN_EMAIL, 5L);

        verify(pointTransactionRepository, never()).countByAccount_IdAndReasonAndKstDate(anyLong(), anyString(), any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void takesBackTheResultPointOfADeletedMatchOnce() {
        PointAccount account = stubAccount(ADMIN_EMAIL, 7L);
        PointTransaction grant = new PointTransaction();
        grant.setAccount(account);
        grant.setReason(PointService.REASON_MATCH_RESULT);
        grant.setAmount(1);
        grant.setReferenceKey("match:5");
        when(pointTransactionRepository.findByReasonAndReferenceKey(PointService.REASON_MATCH_RESULT, "match:5"))
            .thenReturn(List.of(grant));

        pointService.reverseMatchResultPoints(5L);

        PointTransaction saved = captureSavedTransaction();
        assertThat(saved.getReason()).isEqualTo(PointService.REASON_MATCH_RESULT_REVERSED);
        assertThat(saved.getAmount()).isEqualTo(-1);
        assertThat(saved.getReferenceKey()).isEqualTo("match:5");

        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            7L, PointService.REASON_MATCH_RESULT_REVERSED, "match:5"
        )).thenReturn(true);

        pointService.reverseMatchResultPoints(5L);

        verify(pointTransactionRepository).save(any());
    }

    @Test
    void paysAResultConfirmationOncePerMatch() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_MATCH_CONFIRM, TODAY))
            .thenReturn(9L);

        assertThat(pointService.grantMatchConfirmPoint(ADMIN_EMAIL, 5L)).isEqualTo(PointService.MatchConfirmOutcome.CONFIRMED);

        PointTransaction saved = captureSavedTransaction();
        assertThat(saved.getReason()).isEqualTo(PointService.REASON_MATCH_CONFIRM);
        assertThat(saved.getAmount()).isEqualTo(1);
        assertThat(saved.getReferenceKey()).isEqualTo("match:5");
        assertThat(saved.getKstDate()).isEqualTo(TODAY);
    }

    @Test
    void neverPaysTwiceForConfirmingTheSameMatch() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            7L, PointService.REASON_MATCH_CONFIRM, "match:5"
        )).thenReturn(true);

        assertThat(pointService.grantMatchConfirmPoint(ADMIN_EMAIL, 5L))
            .isEqualTo(PointService.MatchConfirmOutcome.ALREADY_CONFIRMED);

        verify(pointTransactionRepository, never()).countByAccount_IdAndReasonAndKstDate(anyLong(), anyString(), any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void stopsResultConfirmationsAtTheDailyCap() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_MATCH_CONFIRM, TODAY))
            .thenReturn(10L);

        assertThat(pointService.grantMatchConfirmPoint(ADMIN_EMAIL, 5L))
            .isEqualTo(PointService.MatchConfirmOutcome.DAILY_CAP_REACHED);

        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void paysNoResultConfirmationWhilePointsAreClosedToTheAccount() {
        assertThat(pointService.grantMatchConfirmPoint(MEMBER_EMAIL, 5L))
            .isEqualTo(PointService.MatchConfirmOutcome.NOT_ALLOWED);

        verifyNoInteractions(pointAccountRepository, pointTransactionRepository);
    }

    @Test
    void takesBackEveryConfirmationOfADeletedMatchOnceInAccountOrder() {
        PointAccount second = stubAccount(MEMBER_EMAIL, 8L);
        PointAccount first = stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.findByReasonAndReferenceKey(PointService.REASON_MATCH_CONFIRM, "match:5"))
            .thenReturn(List.of(grant(second, PointService.REASON_MATCH_CONFIRM), grant(first, PointService.REASON_MATCH_CONFIRM)));

        pointService.reverseMatchConfirmPoints(5L);

        InOrder lockOrder = Mockito.inOrder(pointAccountRepository);
        lockOrder.verify(pointAccountRepository).findByNormalizedEmailForUpdate(ADMIN_EMAIL);
        lockOrder.verify(pointAccountRepository).findByNormalizedEmailForUpdate(MEMBER_EMAIL);
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(PointTransaction::getAccount, PointTransaction::getReason, PointTransaction::getAmount, PointTransaction::getReferenceKey)
            .containsExactly(
                tuple(first, PointService.REASON_MATCH_CONFIRM_REVERSED, -1, "match:5"),
                tuple(second, PointService.REASON_MATCH_CONFIRM_REVERSED, -1, "match:5")
            );

        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(anyLong(), eq(PointService.REASON_MATCH_CONFIRM_REVERSED), eq("match:5")))
            .thenReturn(true);
        pointService.reverseMatchConfirmPoints(5L);

        verify(pointTransactionRepository, Mockito.times(2)).save(any());
    }

    @Test
    void tellsWhichMatchesWereConfirmedAndHowManyToday() {
        existingAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.findByAccount_IdAndReasonAndReferenceKeyIn(
            eq(7L), eq(PointService.REASON_MATCH_CONFIRM), any()
        )).thenReturn(List.of(row(PointService.REASON_MATCH_CONFIRM, 1, null, "match:11")));
        when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_MATCH_CONFIRM, TODAY))
            .thenReturn(4L);

        PointService.MatchConfirmState state = pointService.getMatchConfirmState(ADMIN_EMAIL, List.of(10L, 11L));

        assertThat(state.confirmedMatchIds()).isEqualTo(Set.of(11L));
        assertThat(state.confirmedToday()).isEqualTo(4);
        assertThat(pointService.getMatchConfirmState(MEMBER_EMAIL, List.of(10L)))
            .isEqualTo(new PointService.MatchConfirmState(Set.of(), 0));
    }

    @Test
    void paysReadingANoticeOncePerRevision() {
        stubAccount(ADMIN_EMAIL, 7L);

        pointService.grantNoticeReadPoint(ADMIN_EMAIL, 5L, 0);
        pointService.grantNoticeReadPoint(ADMIN_EMAIL, 5L, 1);
        pointService.grantNoticeReadPoint(ADMIN_EMAIL, 5L, 2);

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(PointTransaction::getReason, PointTransaction::getAmount, PointTransaction::getReferenceKey)
            .containsExactly(
                tuple(PointService.REASON_NOTICE_READ, 1, "notice:5"),
                tuple(PointService.REASON_NOTICE_READ, 1, "notice:5:r1"),
                tuple(PointService.REASON_NOTICE_READ, 1, "notice:5:r2")
            );
    }

    @Test
    void neverPaysTheSameRevisionOfANoticeTwice() {
        stubAccount(ADMIN_EMAIL, 7L);
        // Paid for the first posting before revisions existed, and for revision 1 already.
        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(7L, PointService.REASON_NOTICE_READ, "notice:5"))
            .thenReturn(true);
        when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(7L, PointService.REASON_NOTICE_READ, "notice:5:r1"))
            .thenReturn(true);

        pointService.grantNoticeReadPoint(ADMIN_EMAIL, 5L, 0);
        pointService.grantNoticeReadPoint(ADMIN_EMAIL, 5L, 1);
        pointService.grantNoticePoint(ADMIN_EMAIL, 5L, PointService.REASON_NOTICE_READ);

        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void keepsNoticeLikesAndCommentsOncePerNotice() {
        stubAccount(ADMIN_EMAIL, 7L);

        pointService.grantNoticePoint(ADMIN_EMAIL, 5L, PointService.REASON_NOTICE_LIKE);
        pointService.grantNoticePoint(ADMIN_EMAIL, 5L, PointService.REASON_NOTICE_COMMENT);
        pointService.grantNoticePoint(ADMIN_EMAIL, 5L, "SOMETHING_ELSE");

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(PointTransaction::getReason, PointTransaction::getReferenceKey)
            .containsExactly(
                tuple(PointService.REASON_NOTICE_LIKE, "notice:5"),
                tuple(PointService.REASON_NOTICE_COMMENT, "notice:5")
            );
    }

    @Test
    void paysALikeOnACommentOncePerComment() {
        stubAccount(ADMIN_EMAIL, 7L);
        lenient().when(pointTransactionRepository.existsByAccount_IdAndReasonAndReferenceKey(
            7L, PointService.REASON_NOTICE_COMMENT_LIKE, "notice-comment:31"
        )).thenReturn(true);

        pointService.grantNoticeCommentLikePoint(ADMIN_EMAIL, 30L);
        // Liked, taken back and liked again: paid the first time only.
        pointService.grantNoticeCommentLikePoint(ADMIN_EMAIL, 31L);
        pointService.grantNoticeCommentLikePoint(ADMIN_EMAIL, null);

        PointTransaction saved = captureSavedTransaction();
        assertThat(saved.getReason()).isEqualTo(PointService.REASON_NOTICE_COMMENT_LIKE);
        assertThat(saved.getAmount()).isEqualTo(1);
        assertThat(saved.getReferenceKey()).isEqualTo("notice-comment:30");
        assertThat(saved.getKstDate()).isEqualTo(TODAY);
    }

    @Test
    void stopsPayingCommentLikesAtTheDailyCap() {
        stubAccount(ADMIN_EMAIL, 7L);
        lenient().when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_NOTICE_COMMENT_LIKE, TODAY))
            .thenReturn(9L, 10L);

        pointService.grantNoticeCommentLikePoint(ADMIN_EMAIL, 40L);
        pointService.grantNoticeCommentLikePoint(ADMIN_EMAIL, 41L);

        assertThat(captureSavedTransaction().getReferenceKey()).isEqualTo("notice-comment:40");
    }

    @Test
    void paysNoCommentLikeWhilePointsAreClosedToTheAccountOrTurnedOff() {
        pointService.grantNoticeCommentLikePoint(MEMBER_EMAIL, 30L);
        pointProperties.setNoticeCommentLike(0);
        pointService.grantNoticeCommentLikePoint(ADMIN_EMAIL, 30L);

        verifyNoInteractions(pointAccountRepository, pointTransactionRepository);
    }

    @Test
    void summarizesTodaysCommentLikes() {
        existingAccount(ADMIN_EMAIL, 7L);
        lenient().when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_NOTICE_COMMENT_LIKE, TODAY))
            .thenReturn(4L);

        PointSummaryResponse summary = pointService.getSummary(ADMIN_EMAIL);

        assertThat(summary.noticeCommentLikesToday()).isEqualTo(4);
        assertThat(summary.noticeCommentLikeDailyCap()).isEqualTo(10);
        assertThat(summary.noticeCommentLikePoints()).isEqualTo(1);
    }

    @Test
    void paysNoNoticePointsWhilePointsAreClosedToTheAccount() {
        pointService.grantNoticeReadPoint(MEMBER_EMAIL, 5L, 1);
        pointService.grantNoticePoint(MEMBER_EMAIL, 5L, PointService.REASON_NOTICE_LIKE);

        verifyNoInteractions(pointAccountRepository, pointTransactionRepository);
    }

    @Test
    void ranksTheMonthWithSharedPlacesAndNicknamesOnly() {
        when(pointTransactionRepository.sumPositiveAccountTotalsBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            .thenReturn(List.of(
                new Total(11L, "a@example.com", 5L),
                new Total(12L, "b@example.com", 3L),
                new Total(13L, "c@example.com", 3L),
                new Total(14L, "d@example.com", 1L)
            ));
        when(accessControlService.resolveDisplayNicknames(List.of("a@example.com", "b@example.com", "c@example.com", "d@example.com")))
            .thenReturn(Map.of("a@example.com", "A", "b@example.com", "B", "c@example.com", "C", "d@example.com", "D"));

        PointRankingResponse ranking = pointService.getMonthlyRanking(YearMonth.of(2026, 9));

        assertThat(ranking.month()).isEqualTo("2026-09");
        assertThat(ranking.entries())
            .extracting(PointRankingEntryResponse::rank, PointRankingEntryResponse::nickname, PointRankingEntryResponse::points)
            .containsExactly(
                tuple(1, "A", 5L),
                tuple(2, "B", 3L),
                tuple(2, "C", 3L),
                tuple(4, "D", 1L)
            );
        assertThat(ranking.entries()).extracting(PointRankingEntryResponse::accountId).containsExactly(11L, 12L, 13L, 14L);
    }

    @Test
    void showsHowAnAccountEarnedItsPointsInAMonth() {
        PointAccount account = new PointAccount();
        account.setNormalizedEmail(MEMBER_EMAIL);
        ReflectionTestUtils.setField(account, "id", 21L);
        when(pointAccountRepository.findById(21L)).thenReturn(Optional.of(account));
        when(pointTransactionRepository.findByAccount_IdAndKstDateBetweenOrderByIdDesc(
            21L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        )).thenReturn(List.of(
            row(PointService.REASON_PREDICTION_HIT_REVERSED, -1, null),
            row(PointService.REASON_PREDICTION_HIT, 1, null),
            row(PointService.REASON_ADJUSTMENT, 5, "YOUR_MEMO"),
            row(PointService.REASON_DAILY_LOGIN, 1, null),
            row(PointService.REASON_DAILY_LOGIN, 1, null)
        ));
        when(accessControlService.resolveDisplayNicknames(List.of(MEMBER_EMAIL))).thenReturn(Map.of(MEMBER_EMAIL, "YOUR_USERNAME"));

        PointMonthlyHistoryResponse history = pointService.getMonthlyHistory(21L, YearMonth.of(2026, 9), ADMIN_EMAIL);

        assertThat(history.nickname()).isEqualTo("YOUR_USERNAME");
        assertThat(history.points()).isEqualTo(7L);
        assertThat(history.reasons())
            .extracting(PointReasonTotalResponse::reason, PointReasonTotalResponse::count, PointReasonTotalResponse::points)
            .containsExactly(
                tuple(PointService.REASON_ADJUSTMENT, 1, 5L),
                tuple(PointService.REASON_DAILY_LOGIN, 2, 2L),
                tuple(PointService.REASON_PREDICTION_HIT, 1, 1L),
                tuple(PointService.REASON_PREDICTION_HIT_REVERSED, 1, -1L)
            );
        assertThat(history.entries()).hasSize(5);
        // Someone else's rows come with their day, never the memo or the time of day.
        assertThat(history.entries()).extracting(PointHistoryItemResponse::memo).containsOnlyNulls();
        assertThat(history.entries()).extracting(PointHistoryItemResponse::createdAt).containsOnlyNulls();
        assertThat(history.entries()).extracting(PointHistoryItemResponse::kstDate).containsOnly(LocalDate.of(2026, 9, 15));
        List<PointHistoryItemResponse> own = pointService
            .getMonthlyHistory(21L, YearMonth.of(2026, 9), " " + MEMBER_EMAIL.toUpperCase(java.util.Locale.ROOT) + " ")
            .entries();
        assertThat(own).extracting(PointHistoryItemResponse::memo).contains("YOUR_MEMO");
        assertThat(own).extracting(PointHistoryItemResponse::createdAt).doesNotContainNull();
        when(accessControlService.isSuperAdminEmail("super@example.com")).thenReturn(true);
        List<PointHistoryItemResponse> forSuperAdmin = pointService
            .getMonthlyHistory(21L, YearMonth.of(2026, 9), "super@example.com")
            .entries();
        assertThat(forSuperAdmin).extracting(PointHistoryItemResponse::memo).contains("YOUR_MEMO");
        assertThat(forSuperAdmin).extracting(PointHistoryItemResponse::createdAt).doesNotContainNull();
        assertThatThrownBy(() -> pointService.getMonthlyHistory(99L, YearMonth.of(2026, 9), ADMIN_EMAIL))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void usesTheKoreanMonthWhenNoneIsGiven() {
        assertThat(pointService.currentMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void paysAPredictionHitOnceAndTakesItBackWhenTheResultFlips() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.sumAmountByReferencePattern(7L, "prediction:30#%")).thenReturn(0L);

        pointService.syncPredictionPoint(ADMIN_EMAIL, 30L, true);

        PointTransaction granted = captureSavedTransaction();
        assertThat(granted.getReason()).isEqualTo(PointService.REASON_PREDICTION_HIT);
        assertThat(granted.getAmount()).isEqualTo(1);
        assertThat(granted.getReferenceKey()).isEqualTo("prediction:30#1");

        when(pointTransactionRepository.sumAmountByReferencePattern(7L, "prediction:30#%")).thenReturn(1L);
        when(pointTransactionRepository.countByAccount_IdAndReferenceKeyStartingWith(7L, "prediction:30#")).thenReturn(1L);
        pointService.syncPredictionPoint(ADMIN_EMAIL, 30L, true);
        pointService.syncPredictionPoint(ADMIN_EMAIL, 30L, false);

        ArgumentCaptor<PointTransaction> saved = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        PointTransaction reversed = saved.getAllValues().get(1);
        assertThat(reversed.getReason()).isEqualTo(PointService.REASON_PREDICTION_HIT_REVERSED);
        assertThat(reversed.getAmount()).isEqualTo(-1);
        assertThat(reversed.getReferenceKey()).isEqualTo("prediction:30#2");
    }

    @Test
    void stopsPredictionPointsAtTheDailyCap() {
        stubAccount(ADMIN_EMAIL, 7L);
        when(pointTransactionRepository.sumAmountByReasonsOnDate(eq(7L), any(), eq(TODAY))).thenReturn(10L);

        pointService.syncPredictionPoint(ADMIN_EMAIL, 30L, true);

        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void leavesPeopleWithoutPointsAloneWhenTheirPickMissed() {
        when(pointAccountRepository.findByNormalizedEmail(MEMBER_EMAIL)).thenReturn(Optional.empty());

        pointService.syncPredictionPoint(MEMBER_EMAIL, 30L, false);
        pointService.syncPredictionPoint(MEMBER_EMAIL, 30L, true);

        verify(pointAccountRepository, never()).insertIfMissing(any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void summarizesAnAccountThatHasNoPointsYet() {
        when(pointAccountRepository.findByNormalizedEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());

        PointSummaryResponse summary = pointService.getSummary(ADMIN_EMAIL);

        assertThat(summary.balance()).isZero();
        assertThat(summary.dailyLoginEarnedToday()).isFalse();
        assertThat(summary.dailyLoginPoints()).isEqualTo(1);
        assertThat(summary.matchResultsToday()).isZero();
        assertThat(summary.matchResultDailyCap()).isEqualTo(10);
        assertThat(summary.matchResultPoints()).isEqualTo(1);
        assertThat(summary.matchConfirmsToday()).isZero();
        assertThat(summary.matchConfirmDailyCap()).isEqualTo(10);
        assertThat(summary.matchConfirmPoints()).isEqualTo(1);
        assertThat(summary.matchConfirmWindowHours()).isEqualTo(48);
        assertThat(summary.noticeCommentLikesToday()).isZero();
        assertThat(summary.noticeCommentLikeDailyCap()).isEqualTo(10);
        assertThat(summary.noticeCommentLikePoints()).isEqualTo(1);
        assertThat(summary.recent()).isEmpty();
    }

    @Test
    void summarizesTodaysResultConfirmations() {
        existingAccount(ADMIN_EMAIL, 7L);
        lenient().when(pointTransactionRepository.countByAccount_IdAndReasonAndKstDate(7L, PointService.REASON_MATCH_CONFIRM, TODAY))
            .thenReturn(3L);

        assertThat(pointService.getSummary(ADMIN_EMAIL).matchConfirmsToday()).isEqualTo(3);
    }

    private PointAccount stubAccount(String email, Long id) {
        PointAccount account = new PointAccount();
        account.setNormalizedEmail(email);
        ReflectionTestUtils.setField(account, "id", id);
        when(pointAccountRepository.findByNormalizedEmailForUpdate(email)).thenReturn(Optional.of(account));
        lenient().when(pointAccountRepository.findByNormalizedEmail(email)).thenReturn(Optional.of(account));
        return account;
    }

    private void existingAccount(String email, Long id) {
        PointAccount account = new PointAccount();
        account.setNormalizedEmail(email);
        ReflectionTestUtils.setField(account, "id", id);
        when(pointAccountRepository.findByNormalizedEmail(email)).thenReturn(Optional.of(account));
    }

    private PointTransaction captureSavedTransaction() {
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private PointTransaction row(String reason, int amount, String memo) {
        PointTransaction transaction = new PointTransaction();
        transaction.setReason(reason);
        transaction.setAmount(amount);
        transaction.setKstDate(LocalDate.of(2026, 9, 15));
        transaction.setMemo(memo);
        return transaction;
    }

    private PointTransaction row(String reason, int amount, String memo, String referenceKey) {
        PointTransaction transaction = row(reason, amount, memo);
        transaction.setReferenceKey(referenceKey);
        return transaction;
    }

    private PointTransaction grant(PointAccount account, String reason) {
        PointTransaction grant = row(reason, 1, null, "match:5");
        grant.setAccount(account);
        return grant;
    }

    private record Total(Long accountId, String normalizedEmail, Long points)
        implements PointTransactionRepository.AccountPointTotal {
        @Override
        public Long getAccountId() {
            return accountId;
        }

        @Override
        public String getNormalizedEmail() {
            return normalizedEmail;
        }

        @Override
        public Long getPoints() {
            return points;
        }
    }
}
