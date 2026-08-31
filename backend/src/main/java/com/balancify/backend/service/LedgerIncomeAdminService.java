package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerImportRequest;
import com.balancify.backend.api.group.dto.LedgerImportResponse;
import com.balancify.backend.api.group.dto.LedgerImportRowError;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryUpdateRequest;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerIncomeAdminService {

    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final int MAX_MEMO_LENGTH = 500;

    private final LedgerIncomeEntryRepository ledgerIncomeEntryRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;

    public LedgerIncomeAdminService(
        LedgerIncomeEntryRepository ledgerIncomeEntryRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.ledgerIncomeEntryRepository = ledgerIncomeEntryRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional
    public LedgerIncomeEntryResponse createEntry(
        Long groupId,
        LedgerIncomeEntryCreateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);

        LedgerIncomeEntry entry = new LedgerIncomeEntry();
        entry.setGroupId(groupId);
        entry.setEntryDate(requireEntryDate(request == null ? null : request.entryDate()));
        entry.setCategory(requireCategory(request == null ? null : request.category()));
        entry.setAmount(requireAmount(request == null ? null : request.amount()));
        entry.setMemo(normalizeMemo(request == null ? null : request.memo()));
        entry.setAuthorEmail(normalizeEmail(actorEmail));
        ledgerIncomeEntryRepository.save(entry);

        operationAuditLogService.recordLedgerIncomeAdded(actorEmail, actorNickname, groupId, entry);

        return toResponse(entry, actorNickname);
    }

    @Transactional
    public LedgerIncomeEntryResponse updateEntry(
        Long groupId,
        Long entryId,
        LedgerIncomeEntryUpdateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);

        LedgerIncomeEntry entry = ledgerIncomeEntryRepository.findByIdAndGroupId(entryId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Ledger income entry not found"));
        entry.setEntryDate(requireEntryDate(request == null ? null : request.entryDate()));
        entry.setCategory(requireCategory(request == null ? null : request.category()));
        entry.setAmount(requireAmount(request == null ? null : request.amount()));
        entry.setMemo(normalizeMemo(request == null ? null : request.memo()));
        ledgerIncomeEntryRepository.save(entry);

        operationAuditLogService.recordLedgerIncomeUpdated(actorEmail, actorNickname, groupId, entry);

        String authorNickname = safeTrim(accessControlService.resolveAccessProfile(entry.getAuthorEmail()).nickname());
        return toResponse(entry, authorNickname.isEmpty() ? null : authorNickname);
    }

    @Transactional
    public void deleteEntry(Long groupId, Long entryId, String actorEmail, String actorNickname) {
        requireAdmin(actorEmail);

        LedgerIncomeEntry entry = ledgerIncomeEntryRepository.findByIdAndGroupId(entryId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Ledger income entry not found"));
        String label = entry.getEntryDate() + " " + entry.getCategory();
        ledgerIncomeEntryRepository.delete(entry);

        operationAuditLogService.recordLedgerIncomeDeleted(actorEmail, actorNickname, groupId, entryId, label);
    }

    @Transactional
    public LedgerImportResponse importEntries(
        Long groupId,
        LedgerImportRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);

        String csvContent = request == null ? null : request.csvContent();
        List<List<String>> rows = LedgerCsvParser.parseDataRows(csvContent);

        int importedCount = 0;
        List<LedgerImportRowError> skippedRows = new ArrayList<>();
        String authorEmail = normalizeEmail(actorEmail);

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            List<String> row = rows.get(i);
            try {
                LedgerIncomeEntry entry = new LedgerIncomeEntry();
                entry.setGroupId(groupId);
                entry.setEntryDate(requireEntryDate(parseDate(LedgerCsvParser.cell(row, 0))));
                entry.setAmount(requireAmount(parseAmount(LedgerCsvParser.cell(row, 1))));
                entry.setCategory(requireCategory(LedgerCsvParser.cell(row, 2)));
                entry.setMemo(normalizeMemo(LedgerCsvParser.cell(row, 3)));
                entry.setAuthorEmail(authorEmail);
                ledgerIncomeEntryRepository.save(entry);
                importedCount++;
            } catch (IllegalArgumentException illegalArgumentException) {
                skippedRows.add(new LedgerImportRowError(rowNumber, illegalArgumentException.getMessage()));
            }
        }

        operationAuditLogService.recordLedgerIncomeImported(actorEmail, actorNickname, groupId, importedCount);

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

    private LedgerIncomeEntryResponse toResponse(LedgerIncomeEntry entry, String authorNickname) {
        return new LedgerIncomeEntryResponse(
            entry.getId(),
            entry.getEntryDate(),
            entry.getCategory(),
            entry.getAmount() == null ? 0L : entry.getAmount(),
            entry.getMemo(),
            authorNickname,
            entry.getCreatedAt()
        );
    }
}
