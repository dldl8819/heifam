package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerDashboardBalancePoint;
import com.balancify.backend.api.group.dto.LedgerDashboardCategoryItem;
import com.balancify.backend.api.group.dto.LedgerDashboardMonthItem;
import com.balancify.backend.api.group.dto.LedgerDashboardResponse;
import com.balancify.backend.api.group.dto.LedgerMonthlySummaryItem;
import com.balancify.backend.api.group.dto.LedgerMonthlySummaryResponse;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.domain.LedgerServerCost;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
import com.balancify.backend.repository.LedgerServerCostRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerSummaryServiceTest {

    @Mock
    private LedgerIncomeEntryRepository ledgerIncomeEntryRepository;

    @Mock
    private LedgerExpenseEntryRepository ledgerExpenseEntryRepository;

    @Mock
    private LedgerServerCostRepository ledgerServerCostRepository;

    private LedgerSummaryService ledgerSummaryService;

    @BeforeEach
    void setUp() {
        ledgerSummaryService = new LedgerSummaryService(
            ledgerIncomeEntryRepository,
            ledgerExpenseEntryRepository,
            ledgerServerCostRepository
        );
    }

    private LedgerIncomeEntry income(LocalDate date, String category, long amount) {
        LedgerIncomeEntry entry = new LedgerIncomeEntry();
        entry.setGroupId(1L);
        entry.setEntryDate(date);
        entry.setCategory(category);
        entry.setAmount(amount);
        entry.setAuthorEmail("ops@hei.gg");
        return entry;
    }

    private LedgerExpenseEntry expense(LocalDate date, String expenseType, long amount) {
        LedgerExpenseEntry entry = new LedgerExpenseEntry();
        entry.setGroupId(1L);
        entry.setEntryDate(date);
        entry.setExpenseType(expenseType);
        entry.setCategory("YOUR_CATEGORY");
        entry.setAmount(amount);
        entry.setAuthorEmail("ops@hei.gg");
        return entry;
    }

    private LedgerExpenseEntry expense(LocalDate date, String expenseType, String category, long amount) {
        LedgerExpenseEntry entry = expense(date, expenseType, amount);
        entry.setCategory(category);
        return entry;
    }

    @Test
    void dashboardReportsStartingBalanceSeparatelyAndReconcilesCurrentBalance() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 2, 1), "후원", 50_000L),
            income(LocalDate.of(2026, 3, 3), "기초 잔액", 1_057_801L),
            income(LocalDate.of(2026, 4, 1), "후원", 10_000L),
            income(LocalDate.of(2026, 4, 1), "후원", 100_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            expense(LocalDate.of(2026, 3, 6), "VARIABLE", "리그전", 150_000L),
            expense(LocalDate.of(2026, 4, 6), "VARIABLE", "정기감전", 30_000L),
            expense(LocalDate.of(2026, 4, 10), "FIXED", "서버비", 9_000L)
        ));

        LedgerDashboardResponse dashboard = ledgerSummaryService.getDashboard(1L);

        assertThat(dashboard.startingBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(dashboard.asOfDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(dashboard.startingBalance()).isEqualTo(1_057_801L);
        assertThat(dashboard.totalIncome()).isEqualTo(110_000L);
        assertThat(dashboard.incomeCount()).isEqualTo(2);
        assertThat(dashboard.totalFixedExpense()).isEqualTo(9_000L);
        assertThat(dashboard.totalVariableExpense()).isEqualTo(180_000L);
        assertThat(dashboard.totalExpense()).isEqualTo(189_000L);
        assertThat(dashboard.expenseCount()).isEqualTo(3);
        assertThat(dashboard.currentBalance())
            .isEqualTo(dashboard.startingBalance() + dashboard.totalIncome() - dashboard.totalExpense())
            .isEqualTo(978_801L);

        assertThat(dashboard.balanceTimeline())
            .extracting(
                LedgerDashboardBalancePoint::date,
                LedgerDashboardBalancePoint::change,
                LedgerDashboardBalancePoint::balance
            )
            .containsExactly(
                tuple(LocalDate.of(2026, 3, 3), 1_057_801L, 1_057_801L),
                tuple(LocalDate.of(2026, 3, 6), -150_000L, 907_801L),
                tuple(LocalDate.of(2026, 4, 1), 110_000L, 1_017_801L),
                tuple(LocalDate.of(2026, 4, 6), -30_000L, 987_801L),
                tuple(LocalDate.of(2026, 4, 10), -9_000L, 978_801L)
            );

        assertThat(dashboard.months()).hasSize(2);
        LedgerDashboardMonthItem march = dashboard.months().get(0);
        assertThat(march.month()).isEqualTo("2026-03");
        assertThat(march.income()).isZero();
        assertThat(march.totalExpense()).isEqualTo(150_000L);
        assertThat(march.net()).isEqualTo(-150_000L);
        assertThat(march.endBalance()).isEqualTo(907_801L);
        LedgerDashboardMonthItem april = dashboard.months().get(1);
        assertThat(april.month()).isEqualTo("2026-04");
        assertThat(april.income()).isEqualTo(110_000L);
        assertThat(april.incomeCount()).isEqualTo(2);
        assertThat(april.fixedExpense()).isEqualTo(9_000L);
        assertThat(april.variableExpense()).isEqualTo(30_000L);
        assertThat(april.expenseCount()).isEqualTo(2);
        assertThat(april.net()).isEqualTo(71_000L);
        assertThat(april.endBalance()).isEqualTo(978_801L);

        assertThat(dashboard.expenseCategories())
            .extracting(
                LedgerDashboardCategoryItem::category,
                LedgerDashboardCategoryItem::amount,
                LedgerDashboardCategoryItem::count
            )
            .containsExactly(
                tuple("리그전", 150_000L, 1),
                tuple("정기감전", 30_000L, 1),
                tuple("서버비", 9_000L, 1)
            );
    }

    private LedgerServerCost serverCost(String billingMonth, Long krwAmount, LocalDate reimbursedDate) {
        LedgerServerCost cost = new LedgerServerCost();
        cost.setGroupId(1L);
        cost.setServiceName("Render");
        cost.setBillingMonth(billingMonth);
        cost.setChargedDate(LocalDate.parse(billingMonth + "-01").plusMonths(1));
        cost.setKrwAmount(krwAmount);
        cost.setReimbursedDate(reimbursedDate);
        cost.setAuthorEmail("ops@hei.gg");
        return cost;
    }

    @Test
    void dashboardSubtractsOnlyReimbursedServerCostsAndKeepsThemOutOfExpenses() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 3, 3), "기초 잔액", 100_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            expense(LocalDate.of(2026, 4, 6), "VARIABLE", "정기감전", 30_000L)
        ));
        when(ledgerServerCostRepository.findByGroupIdOrderByBillingMonthDescIdDesc(1L)).thenReturn(List.of(
            serverCost("2026-04", 10_000L, LocalDate.of(2026, 5, 20)),
            serverCost("2026-05", 9_500L, null),
            serverCost("2026-06", null, null)
        ));

        LedgerDashboardResponse dashboard = ledgerSummaryService.getDashboard(1L);

        assertThat(dashboard.totalExpense()).isEqualTo(30_000L);
        assertThat(dashboard.expenseCategories())
            .extracting(LedgerDashboardCategoryItem::category)
            .containsExactly("정기감전");
        assertThat(dashboard.serverCostReimbursed()).isEqualTo(10_000L);
        assertThat(dashboard.serverCostPending()).isEqualTo(9_500L);
        assertThat(dashboard.serverCostMissingKrwCount()).isEqualTo(1);
        assertThat(dashboard.currentBalance())
            .isEqualTo(dashboard.startingBalance() + dashboard.totalIncome() - dashboard.totalExpense()
                - dashboard.serverCostReimbursed())
            .isEqualTo(60_000L);
        assertThat(dashboard.asOfDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(dashboard.balanceTimeline())
            .extracting(LedgerDashboardBalancePoint::date, LedgerDashboardBalancePoint::balance)
            .containsExactly(
                tuple(LocalDate.of(2026, 3, 3), 100_000L),
                tuple(LocalDate.of(2026, 4, 6), 70_000L),
                tuple(LocalDate.of(2026, 5, 20), 60_000L)
            );

        LedgerDashboardMonthItem may = dashboard.months().get(2);
        assertThat(may.month()).isEqualTo("2026-05");
        assertThat(may.totalExpense()).isZero();
        assertThat(may.serverCostReimbursed()).isEqualTo(10_000L);
        assertThat(may.net()).isEqualTo(-10_000L);
        assertThat(may.endBalance()).isEqualTo(60_000L);
    }

    @Test
    void dashboardFillsMonthsWithoutEntriesAndCarriesTheBalanceForward() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 1, 15), "기초 잔액", 100_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            expense(LocalDate.of(2026, 3, 2), "VARIABLE", 1_000L)
        ));

        LedgerDashboardResponse dashboard = ledgerSummaryService.getDashboard(1L);

        assertThat(dashboard.months())
            .extracting(
                LedgerDashboardMonthItem::month,
                LedgerDashboardMonthItem::totalExpense,
                LedgerDashboardMonthItem::endBalance
            )
            .containsExactly(
                tuple("2026-01", 0L, 100_000L),
                tuple("2026-02", 0L, 100_000L),
                tuple("2026-03", 1_000L, 99_000L)
            );
    }

    @Test
    void dashboardIsEmptyWhenTheLedgerHasNoEntries() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of());
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of());

        LedgerDashboardResponse dashboard = ledgerSummaryService.getDashboard(1L);

        assertThat(dashboard.asOfDate()).isNull();
        assertThat(dashboard.startingBalanceDate()).isNull();
        assertThat(dashboard.currentBalance()).isZero();
        assertThat(dashboard.balanceTimeline()).isEmpty();
        assertThat(dashboard.months()).isEmpty();
        assertThat(dashboard.expenseCategories()).isEmpty();
    }

    @Test
    void cumulativeBalanceStartsFromStartingBalanceCategoryAndAccumulatesChronologically() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 1, 10), "기초 잔액", 100_000L),
            income(LocalDate.of(2026, 2, 5), "YOUR_CATEGORY", 20_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            expense(LocalDate.of(2026, 2, 10), "FIXED", 5_000L),
            expense(LocalDate.of(2026, 3, 1), "VARIABLE", 3_000L)
        ));

        LedgerMonthlySummaryResponse response = ledgerSummaryService.getMonthlySummary(1L, 2026);

        assertThat(response.months().get(0).cumulativeBalance()).isEqualTo(100_000L);
        assertThat(response.months().get(1).cumulativeBalance()).isEqualTo(115_000L);
        assertThat(response.months().get(2).cumulativeBalance()).isEqualTo(112_000L);
        for (int i = 3; i < 12; i++) {
            assertThat(response.months().get(i).cumulativeBalance()).isEqualTo(112_000L);
        }
    }

    @Test
    void ignoresEntriesBeforeTheStartingBalanceDateInCumulativeBalance() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 1, 1), "YOUR_CATEGORY", 999_999L),
            income(LocalDate.of(2026, 2, 1), "기초 잔액", 50_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of());

        LedgerMonthlySummaryResponse response = ledgerSummaryService.getMonthlySummary(1L, 2026);

        assertThat(response.months().get(0).cumulativeBalance()).isEqualTo(0L);
        assertThat(response.months().get(1).cumulativeBalance()).isEqualTo(50_000L);
    }

    @Test
    void computesMonthlyIncomeAndSplitExpenseTotals() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 3, 3), "YOUR_CATEGORY", 30_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            expense(LocalDate.of(2026, 3, 4), "FIXED", 4_000L),
            expense(LocalDate.of(2026, 3, 5), "VARIABLE", 6_000L)
        ));

        LedgerMonthlySummaryItem march = ledgerSummaryService.getMonthlySummary(1L, 2026).months().get(2);

        assertThat(march.totalIncome()).isEqualTo(30_000L);
        assertThat(march.totalFixedExpense()).isEqualTo(4_000L);
        assertThat(march.totalVariableExpense()).isEqualTo(6_000L);
        assertThat(march.totalExpense()).isEqualTo(10_000L);
        assertThat(march.net()).isEqualTo(20_000L);
    }

    @Test
    void fallsBackToAccumulatingEverythingWhenNoStartingBalanceEntryExists() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of(
            income(LocalDate.of(2026, 1, 1), "YOUR_CATEGORY", 10_000L)
        ));
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateAscIdAsc(1L)).thenReturn(List.of());

        LedgerMonthlySummaryResponse response = ledgerSummaryService.getMonthlySummary(1L, 2026);

        assertThat(response.months().get(0).cumulativeBalance()).isEqualTo(10_000L);
    }
}
