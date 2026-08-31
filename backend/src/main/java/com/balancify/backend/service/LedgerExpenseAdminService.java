package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryUpdateRequest;
import com.balancify.backend.api.group.dto.LedgerImportRequest;
import com.balancify.backend.api.group.dto.LedgerImportResponse;
import com.balancify.backend.api.group.dto.LedgerImportRowError;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerExpenseAdminService {

    private static final Set<String> VALID_EXPENSE_TYPES = Set.of("FIXED", "VARIABLE");
    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final int MAX_TARGET_LENGTH = 300;
    private static final int MAX_MEMO_LENGTH = 500;

    private final LedgerExpenseEntryRepository ledgerExpenseEntryRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;

    public LedgerExpenseAdminService(
        LedgerExpenseEntryRepository ledgerExpenseEntryRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.ledgerExpenseEntryRepository = ledgerExpenseEntryRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional
    public LedgerExpenseEntryResponse createEntry(
        Long groupId,
        LedgerExpenseEntryCreateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);

        LedgerExpenseEntry entry = new LedgerExpenseEntry();
        entry.setGroupId(groupId);
        entry.setEntryDate(requireEntryDate(request == null ? null : request.entryDate()));
        entry.setExpenseType(requireExpenseType(request == null ? null : request.expenseType()));
        entry.setCategory(requireCategory(request == null ? null : request.category()));
        entry.setTarget(normalizeTarget(request == null ? null : request.target()));
        entry.setAmount(requireAmount(request == null ? null : request.amount()));
        entry.setMemo(normalizeMemo(request == null ? null : request.memo()));
        entry.setAuthorEmail(normalizeEmail(actorEmail));
        ledgerExpenseEntryRepository.save(entry);

        operationAuditLogService.recordLedgerExpenseAdded(actorEmail, actorNickname, groupId, entry);

        return toResponse(entry, actorNickname);
    }

    @Transactional
    public LedgerExpenseEntryResponse updateEntry(
        Long groupId,
        Long entryId,
        LedgerExpenseEntryUpdateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);

        LedgerExpenseEntry entry = ledgerExpenseEntryRepository.findByIdAndGroupId(entryId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Ledger expense entry not found"));
        entry.setEntryDate(requireEntryDate(request == null ? null : request.entryDate()));
        entry.setExpenseType(requireExpenseType(request == null ? null : request.expenseType()));
        entry.setCategory(requireCategory(request == null ? null : request.category()));
        entry.setTarget(normalizeTarget(request == null ? null : request.target()));
        entry.setAmount(requireAmount(request == null ? null : request.amount()));
        entry.setMemo(normalizeMemo(request == null ? null : request.memo()));
        ledgerExpenseEntryRepository.save(entry);

        operationAuditLogService.recordLedgerExpenseUpdated(actorEmail, actorNickname, groupId, entry);

        String authorNickname = safeTrim(accessControlService.resolveAccessProfile(entry.getAuthorEmail()).nickname());
        return toResponse(entry, authorNickname.isEmpty() ? null : authorNickname);
    }

    @Transactional
    public void deleteEntry(Long groupId, Long entryId, String actorEmail, String actorNickname) {
        requireAdmin(actorEmail);

        LedgerExpenseEntry entry = ledgerExpenseEntryRepository.findByIdAndGroupId(entryId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Ledger expense entry not found"));
        String label = entry.getEntryDate() + " " + entry.getExpenseType() + " " + entry.getCategory();
        ledgerExpenseEntryRepository.delete(entry);

        operationAuditLogService.recordLedgerExpenseDeleted(actorEmail, actorNickname, groupId, entryId, label);
    }

    @Transactional
    public LedgerImportResponse importEntries(
        Long groupId,
        LedgerImportRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);

        String expenseType = requireExpenseType(request == null ? null : request.expenseType());
        String csvContent = request == null ? null : request.csvContent();
        List<List<String>> rows = LedgerCsvParser.parseDataRows(csvContent);

        int importedCount = 0;
        List<LedgerImportRowError> skippedRows = new ArrayList<>();
        String authorEmail = normalizeEmail(actorEmail);

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            List<String> row = rows.get(i);
            try {
                LedgerExpenseEntry entry = new LedgerExpenseEntry();
                entry.setGroupId(groupId);
                entry.setEntryDate(requireEntryDate(parseDate(LedgerCsvParser.cell(row, 0))));
                entry.setExpenseType(expenseType);
                entry.setAmount(requireAmount(parseAmount(LedgerCsvParser.cell(row, 1))));
                entry.setCategory(requireCategory(LedgerCsvParser.cell(row, 2)));
                entry.setTarget(normalizeTarget(LedgerCsvParser.cell(row, 3)));
                entry.setMemo(normalizeMemo(LedgerCsvParser.cell(row, 4)));
                entry.setAuthorEmail(authorEmail);
                ledgerExpenseEntryRepository.save(entry);
                importedCount++;
            } catch (IllegalArgumentException illegalArgumentException) {
                skippedRows.add(new LedgerImportRowError(rowNumber, illegalArgumentException.getMessage()));
            }
        }

        operationAuditLogService.recordLedgerExpenseImported(actorEmail, actorNickname, groupId, importedCount);

        return new LedgerImportResponse(importedCount, skippedRows);
    }

    private LocalDate parseDate(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException dateTimeParseException) {
            throw new IllegalArgumentException("Invalid date: " + trimmed);
        }
    }

    private Long parseAmount(String value) {
        String trimmed = safeTrim(value).replace(",", "");
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException("Invalid amount: " + trimmed);
        }
    }

    private void requireAdmin(String actorEmail) {
        if (!accessControlService.isAdminEmail(actorEmail)) {
            throw new IllegalArgumentException("Only admins can manage the ledger");
        }
    }

    private LocalDate requireEntryDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("Entry date is required");
        }
        return value;
    }

    private String requireExpenseType(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (!VALID_EXPENSE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Expense type must be FIXED or VARIABLE");
        }
        return normalized;
    }

    private String requireCategory(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Category is required");
        }
        if (trimmed.length() > MAX_CATEGORY_LENGTH) {
            throw new IllegalArgumentException("Category must be " + MAX_CATEGORY_LENGTH + " characters or fewer");
        }
        return trimmed;
    }

    private Long requireAmount(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Amount must be a positive number");
        }
        return value;
    }

    private String normalizeTarget(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_TARGET_LENGTH ? trimmed.substring(0, MAX_TARGET_LENGTH) : trimmed;
    }

    private String normalizeMemo(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_MEMO_LENGTH ? trimmed.substring(0, MAX_MEMO_LENGTH) : trimmed;
    }

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private LedgerExpenseEntryResponse toResponse(LedgerExpenseEntry entry, String authorNickname) {
        return new LedgerExpenseEntryResponse(
            entry.getId(),
            entry.getEntryDate(),
            entry.getExpenseType(),
            entry.getCategory(),
            entry.getTarget(),
            entry.getAmount() == null ? 0L : entry.getAmount(),
            entry.getMemo(),
            authorNickname,
            entry.getCreatedAt()
        );
    }
}
