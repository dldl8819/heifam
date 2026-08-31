package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.LedgerCategoriesResponse;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryResponse;
import com.balancify.backend.api.group.dto.LedgerMonthlySummaryResponse;
import com.balancify.backend.service.LedgerExpenseService;
import com.balancify.backend.service.LedgerIncomeService;
import com.balancify.backend.service.LedgerSummaryService;
import java.time.Year;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupLedgerController {

    private final LedgerIncomeService ledgerIncomeService;
    private final LedgerExpenseService ledgerExpenseService;
    private final LedgerSummaryService ledgerSummaryService;

    public GroupLedgerController(
        LedgerIncomeService ledgerIncomeService,
        LedgerExpenseService ledgerExpenseService,
        LedgerSummaryService ledgerSummaryService
    ) {
        this.ledgerIncomeService = ledgerIncomeService;
        this.ledgerExpenseService = ledgerExpenseService;
        this.ledgerSummaryService = ledgerSummaryService;
    }

    @GetMapping("/{groupId}/ledger/income")
    public List<LedgerIncomeEntryResponse> getIncomeEntries(@PathVariable Long groupId) {
        return ledgerIncomeService.getEntries(groupId);
    }

    @GetMapping("/{groupId}/ledger/income/categories")
    public LedgerCategoriesResponse getIncomeCategories(@PathVariable Long groupId) {
        return ledgerIncomeService.getCategories(groupId);
    }

    @GetMapping("/{groupId}/ledger/expense")
    public List<LedgerExpenseEntryResponse> getExpenseEntries(
        @PathVariable Long groupId,
        @RequestParam(name = "expense_type", required = false) String expenseType
    ) {
        return ledgerExpenseService.getEntries(groupId, expenseType);
    }

    @GetMapping("/{groupId}/ledger/expense/categories")
    public LedgerCategoriesResponse getExpenseCategories(
        @PathVariable Long groupId,
        @RequestParam(name = "expense_type", required = false) String expenseType
    ) {
        return ledgerExpenseService.getCategories(groupId, expenseType);
    }

    @GetMapping("/{groupId}/ledger/summary")
    public LedgerMonthlySummaryResponse getSummary(
        @PathVariable Long groupId,
        @RequestParam(name = "year", required = false) Integer year
    ) {
        int resolvedYear = year == null ? Year.now().getValue() : year;
        return ledgerSummaryService.getMonthlySummary(groupId, resolvedYear);
    }
}
