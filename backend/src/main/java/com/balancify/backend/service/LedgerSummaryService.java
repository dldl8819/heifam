package com.balancify.backend.service;

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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerSummaryService {

    private static final String STARTING_BALANCE_CATEGORY = "기초 잔액";

    private final LedgerIncomeEntryRepository ledgerIncomeEntryRepository;
    private final LedgerExpenseEntryRepository ledgerExpenseEntryRepository;
    private final LedgerServerCostRepository ledgerServerCostRepository;

    public LedgerSummaryService(
        LedgerIncomeEntryRepository ledgerIncomeEntryRepository,
        LedgerExpenseEntryRepository ledgerExpenseEntryRepository,
        LedgerServerCostRepository ledgerServerCostRepository
    ) {
        this.ledgerIncomeEntryRepository = ledgerIncomeEntryRepository;
        this.ledgerExpenseEntryRepository = ledgerExpenseEntryRepository;
        this.ledgerServerCostRepository = ledgerServerCostRepository;
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

    /**
     * Builds the all-time ledger dashboard: the current balance, the balance after each day with
     * entries, per-month totals and spending by category.
     *
     * <p>Counting starts at the earliest "기초 잔액" entry - the same starting point the monthly
     * summary's cumulative balance uses - and entries dated before it are left out, so
     * {@code startingBalance + totalIncome - totalExpense - serverCostReimbursed} always equals
     * {@code currentBalance}.
     * Starting balance entries are reported on their own rather than as income: they are the money
     * already in the account when record keeping began, not money that came in.
     *
     * <p>Server costs are tracked apart from expenses. A member pays them first, so only the ones
     * the account has paid back (a reimbursed date) reduce the balance, on that date; costs still
     * to be paid back are reported as pending, and costs without a won amount are only counted.
     */
    @Transactional(readOnly = true)
    public LedgerDashboardResponse getDashboard(Long groupId) {
        List<LedgerIncomeEntry> incomeEntries = ledgerIncomeEntryRepository
            .findByGroupIdOrderByEntryDateAscIdAsc(groupId);
        List<LedgerExpenseEntry> expenseEntries = ledgerExpenseEntryRepository
            .findByGroupIdOrderByEntryDateAscIdAsc(groupId);
        List<LedgerServerCost> serverCosts = ledgerServerCostRepository
            .findByGroupIdOrderByBillingMonthDescIdDesc(groupId);
        LocalDate startingBalanceDate = findEarliestStartingBalanceDate(incomeEntries);

        long startingBalance = 0L;
        long totalIncome = 0L;
        int incomeCount = 0;
        long totalFixedExpense = 0L;
        long totalVariableExpense = 0L;
        int expenseCount = 0;
        TreeMap<LocalDate, Long> changeByDate = new TreeMap<>();
        Map<YearMonth, MonthTotals> totalsByMonth = new LinkedHashMap<>();
        Map<String, CategoryTotals> totalsByCategory = new LinkedHashMap<>();

        for (LedgerIncomeEntry entry : incomeEntries) {
            LocalDate date = entry.getEntryDate();
            if (date == null || !isOnOrAfterStartingBalance(date, startingBalanceDate)) {
                continue;
            }
            long amount = safeAmount(entry.getAmount());
            changeByDate.merge(date, amount, Long::sum);
            if (isStartingBalanceCategory(entry.getCategory())) {
                startingBalance += amount;
                continue;
            }
            totalIncome += amount;
            incomeCount++;
            MonthTotals month = totalsByMonth.computeIfAbsent(YearMonth.from(date), ignored -> new MonthTotals());
            month.income += amount;
            month.incomeCount++;
        }

        for (LedgerExpenseEntry entry : expenseEntries) {
            LocalDate date = entry.getEntryDate();
            if (date == null || !isOnOrAfterStartingBalance(date, startingBalanceDate)) {
                continue;
            }
            boolean fixed = "FIXED".equals(entry.getExpenseType());
            if (!fixed && !"VARIABLE".equals(entry.getExpenseType())) {
                continue;
            }
            long amount = safeAmount(entry.getAmount());
            changeByDate.merge(date, -amount, Long::sum);
            expenseCount++;
            MonthTotals month = totalsByMonth.computeIfAbsent(YearMonth.from(date), ignored -> new MonthTotals());
            month.expenseCount++;
            if (fixed) {
                totalFixedExpense += amount;
                month.fixedExpense += amount;
            } else {
                totalVariableExpense += amount;
                month.variableExpense += amount;
            }
            String category = entry.getCategory() == null ? "" : entry.getCategory().trim();
            CategoryTotals categoryTotals = totalsByCategory.computeIfAbsent(category, ignored -> new CategoryTotals());
            categoryTotals.amount += amount;
            categoryTotals.count++;
        }

        long serverCostReimbursed = 0L;
        long serverCostPending = 0L;
        int serverCostMissingKrwCount = 0;
        for (LedgerServerCost serverCost : serverCosts) {
            Long krwAmount = serverCost.getKrwAmount();
            if (krwAmount == null) {
                serverCostMissingKrwCount++;
                continue;
            }
            LocalDate reimbursedDate = serverCost.getReimbursedDate();
            if (reimbursedDate == null) {
                serverCostPending += krwAmount;
                continue;
            }
            if (!isOnOrAfterStartingBalance(reimbursedDate, startingBalanceDate)) {
                continue;
            }
            serverCostReimbursed += krwAmount;
            changeByDate.merge(reimbursedDate, -krwAmount, Long::sum);
            MonthTotals month = totalsByMonth.computeIfAbsent(YearMonth.from(reimbursedDate), ignored -> new MonthTotals());
            month.serverCostReimbursed += krwAmount;
        }

        List<LedgerDashboardBalancePoint> balanceTimeline = new ArrayList<>();
        long balance = 0L;
        for (Map.Entry<LocalDate, Long> day : changeByDate.entrySet()) {
            balance += day.getValue();
            balanceTimeline.add(new LedgerDashboardBalancePoint(day.getKey(), day.getValue(), balance));
        }

        List<LedgerDashboardMonthItem> months = new ArrayList<>();
        if (!changeByDate.isEmpty()) {
            YearMonth lastMonth = YearMonth.from(changeByDate.lastKey());
            int pointIndex = 0;
            long endBalance = 0L;
            for (YearMonth month = YearMonth.from(changeByDate.firstKey()); !month.isAfter(lastMonth); month = month.plusMonths(1)) {
                LocalDate monthEnd = month.atEndOfMonth();
                while (pointIndex < balanceTimeline.size() && !balanceTimeline.get(pointIndex).date().isAfter(monthEnd)) {
                    endBalance = balanceTimeline.get(pointIndex).balance();
                    pointIndex++;
                }
                MonthTotals totals = totalsByMonth.getOrDefault(month, new MonthTotals());
                long monthExpense = totals.fixedExpense + totals.variableExpense;
                months.add(new LedgerDashboardMonthItem(
                    month.toString(),
                    totals.income,
                    totals.incomeCount,
                    totals.fixedExpense,
                    totals.variableExpense,
                    monthExpense,
                    totals.expenseCount,
                    totals.serverCostReimbursed,
                    totals.income - monthExpense - totals.serverCostReimbursed,
                    endBalance
                ));
            }
        }

        List<LedgerDashboardCategoryItem> expenseCategories = totalsByCategory.entrySet()
            .stream()
            .map(entry -> new LedgerDashboardCategoryItem(entry.getKey(), entry.getValue().amount, entry.getValue().count))
            .sorted(Comparator.comparingLong(LedgerDashboardCategoryItem::amount)
                .reversed()
                .thenComparing(LedgerDashboardCategoryItem::category))
            .toList();

        return new LedgerDashboardResponse(
            changeByDate.isEmpty() ? null : changeByDate.lastKey(),
            startingBalanceDate,
            startingBalance,
            totalIncome,
            incomeCount,
            totalFixedExpense,
            totalVariableExpense,
            totalFixedExpense + totalVariableExpense,
            expenseCount,
            balance,
            serverCostReimbursed,
            serverCostPending,
            serverCostMissingKrwCount,
            balanceTimeline,
            months,
            expenseCategories
        );
    }

    private boolean isStartingBalanceCategory(String category) {
        return category != null && STARTING_BALANCE_CATEGORY.equals(category.trim());
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

    private static final class MonthTotals {
        private long income;
        private int incomeCount;
        private long fixedExpense;
        private long variableExpense;
        private int expenseCount;
        private long serverCostReimbursed;
    }

    private static final class CategoryTotals {
        private long amount;
        private int count;
    }
}
