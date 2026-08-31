package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerMonthlySummaryItem;
import com.balancify.backend.api.group.dto.LedgerMonthlySummaryResponse;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
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

    private LedgerSummaryService ledgerSummaryService;

    @BeforeEach
    void setUp() {
        ledgerSummaryService = new LedgerSummaryService(ledgerIncomeEntryRepository, ledgerExpenseEntryRepository);
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
