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

import com.balancify.backend.api.points.dto.PointAdjustmentResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
            operationAuditLogService,
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
    void adjustsAMembersPointsAndLogsIt() {
        stubAccount(MEMBER_EMAIL, 8L);
        when(accessControlService.isServiceAccessAllowed(MEMBER_EMAIL)).thenReturn(true);
        when(accessControlService.resolveDisplayNickname(MEMBER_EMAIL)).thenReturn("YOUR_USERNAME");
        when(pointTransactionRepository.sumAmountByAccountId(8L)).thenReturn(25L);

        PointAdjustmentResponse response = pointService.adjust(
            ADMIN_EMAIL,
            "YOUR_USERNAME",
            " Member@Example.com ",
            20,
            "  " + "x".repeat(250) + "  "
        );

        PointTransaction saved = captureSavedTransaction();
        assertThat(saved.getReason()).isEqualTo(PointService.REASON_ADJUSTMENT);
        assertThat(saved.getAmount()).isEqualTo(20);
        assertThat(saved.getReferenceKey()).startsWith("adjust:");
        assertThat(saved.getMemo()).hasSize(200);
        assertThat(saved.getCreatedByEmail()).isEqualTo(ADMIN_EMAIL);
        verify(operationAuditLogService).recordPointAdjustment(
            eq(ADMIN_EMAIL), eq("YOUR_USERNAME"), eq(8L), eq("YOUR_USERNAME"), eq(20), eq("x".repeat(200))
        );
        assertThat(response.nickname()).isEqualTo("YOUR_USERNAME");
        assertThat(response.amount()).isEqualTo(20);
        assertThat(response.balance()).isEqualTo(25L);
    }

    @Test
    void rejectsAdjustmentsThatAreZeroTooLargeOrForStrangers() {
        assertThatThrownBy(() -> pointService.adjust(ADMIN_EMAIL, null, "not-an-email", 5, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.adjust(ADMIN_EMAIL, null, MEMBER_EMAIL, 0, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.adjust(ADMIN_EMAIL, null, MEMBER_EMAIL, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.adjust(ADMIN_EMAIL, null, MEMBER_EMAIL, -1001, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.adjust(ADMIN_EMAIL, null, MEMBER_EMAIL, 10, null))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(pointAccountRepository, pointTransactionRepository, operationAuditLogService);
    }

    @Test
    void ranksTheMonthWithSharedPlacesAndNicknamesOnly() {
        when(pointTransactionRepository.sumPositiveTotalsBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            .thenReturn(List.of(
                new Total("a@example.com", 5L),
                new Total("b@example.com", 3L),
                new Total("c@example.com", 3L),
                new Total("d@example.com", 1L)
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
    }

    @Test
    void usesTheKoreanMonthWhenNoneIsGiven() {
        assertThat(pointService.currentMonth()).isEqualTo(YearMonth.of(2026, 10));
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
        assertThat(summary.recent()).isEmpty();
    }

    private PointAccount stubAccount(String email, Long id) {
        PointAccount account = new PointAccount();
        account.setNormalizedEmail(email);
        ReflectionTestUtils.setField(account, "id", id);
        when(pointAccountRepository.findByNormalizedEmailForUpdate(email)).thenReturn(Optional.of(account));
        return account;
    }

    private PointTransaction captureSavedTransaction() {
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private record Total(String normalizedEmail, Long points) implements PointTransactionRepository.PointTotal {
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
