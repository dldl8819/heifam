package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerMonthlySummaryItem;
import com.balancify.backend.api.group.dto.LedgerMonthlySummaryResponse;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerSummaryService {

    private static final String STARTING_BALANCE_CATEGORY = "기초 잔액";

    private final LedgerIncomeEntryRepository ledgerIncomeEntryRepository;
    private final LedgerExpenseEntryRepository ledgerExpenseEntryRepository;

    public LedgerSummaryService(
        LedgerIncomeEntryRepository ledgerIncomeEntryRepository,
        LedgerExpenseEntryRepository ledgerExpenseEntryRepository
    ) {
        this.ledgerIncomeEntryRepository = ledgerIncomeEntryRepository;
        this.ledgerExpenseEntryRepository = ledgerExpenseEntryRepository;
    }

    @Transactional(readOnly = true)
    public LedgerMonthlySummaryResponse getMonthlySummary(Long groupId, int year) {
        List<LedgerIncomeEntry> incomeEntries = ledgerIncomeEntryRepository
            .findByGroupIdOrderByEntryDateAscIdAsc(groupId);
        List<LedgerExpenseEntry> expenseEntries = ledgerExpenseEntryRepository
            .findByGroupIdOrderByEntryDateAscIdAsc(groupId);

        LocalDate startingBalanceDate = findEarliestStartingBalanceDate(incomeEntries);

        List<LedgerEvent> timeline = new ArrayList<>();
        for (LedgerIncomeEntry entry : incomeEntries) {
            if (entry.getEntryDate() != null && isOnOrAfterStartingBalance(entry.getEntryDate(), startingBalanceDate)) {
                timeline.add(new LedgerEvent(entry.getEntryDate(), safeAmount(entry.getAmount())));
            }
        }
        for (LedgerExpenseEntry entry : expenseEntries) {
            if (entry.getEntryDate() != null && isOnOrAfterStartingBalance(entry.getEntryDate(), startingBalanceDate)) {
                timeline.add(new LedgerEvent(entry.getEntryDate(), -safeAmount(entry.getAmount())));
            }
        }
        timeline.sort(Comparator.comparing(LedgerEvent::date));

        List<LedgerMonthlySummaryItem> months = new ArrayList<>();
        long cumulativeBalance = 0L;
        int timelineIndex = 0;

        for (int month = 1; month <= 12; month++) {
            LocalDate monthEnd = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);

            long totalIncome = 0L;
            long totalFixedExpense = 0L;
            long totalVariableExpense = 0L;
            for (LedgerIncomeEntry entry : incomeEntries) {
                if (isInMonth(entry.getEntryDate(), year, month)) {
                    totalIncome += safeAmount(entry.getAmount());
                }
            }
            for (LedgerExpenseEntry entry : expenseEntries) {
                if (!isInMonth(entry.getEntryDate(), year, month)) {
                    continue;
                }
                if ("FIXED".equals(entry.getExpenseType())) {
                    totalFixedExpense += safeAmount(entry.getAmount());
                } else if ("VARIABLE".equals(entry.getExpenseType())) {
                    totalVariableExpense += safeAmount(entry.getAmount());
                }
            }

            while (timelineIndex < timeline.size() && !timeline.get(timelineIndex).date().isAfter(monthEnd)) {
                cumulativeBalance += timeline.get(timelineIndex).amount();
                timelineIndex++;
            }

            long totalExpense = totalFixedExpense + totalVariableExpense;
            months.add(new LedgerMonthlySummaryItem(
                month,
                totalIncome,
                totalFixedExpense,
                totalVariableExpense,
                totalExpense,
                totalIncome - totalExpense,
                cumulativeBalance
            ));
        }

        return new LedgerMonthlySummaryResponse(year, months);
    }

    private LocalDate findEarliestStartingBalanceDate(List<LedgerIncomeEntry> incomeEntries) {
        LocalDate earliest = null;
        for (LedgerIncomeEntry entry : incomeEntries) {
            String category = entry.getCategory();
            if (category == null || entry.getEntryDate() == null) {
                continue;
            }
            if (!STARTING_BALANCE_CATEGORY.equals(category.trim())) {
                continue;
            }
            if (earliest == null || entry.getEntryDate().isBefore(earliest)) {
                earliest = entry.getEntryDate();
            }
        }
        return earliest;
    }

    private boolean isOnOrAfterStartingBalance(LocalDate date, LocalDate startingBalanceDate) {
        return startingBalanceDate == null || !date.isBefore(startingBalanceDate);
    }

    private boolean isInMonth(LocalDate date, int year, int month) {
        return date != null && date.getYear() == year && date.getMonthValue() == month;
    }

    private long safeAmount(Long amount) {
        return amount == null ? 0L : amount;
    }

    private record LedgerEvent(LocalDate date, long amount) {
    }
}
