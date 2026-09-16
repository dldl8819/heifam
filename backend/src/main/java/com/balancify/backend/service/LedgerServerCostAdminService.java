package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerServerCostRequest;
import com.balancify.backend.api.group.dto.LedgerServerCostResponse;
import com.balancify.backend.domain.LedgerServerCost;
import com.balancify.backend.repository.LedgerServerCostRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServerCostAdminService {

    private static final int MAX_SERVICE_NAME_LENGTH = 50;
    private static final int MAX_PAID_BY_LENGTH = 100;
    private static final int MAX_MEMO_LENGTH = 500;
    private static final BigDecimal MAX_USD_AMOUNT = new BigDecimal("99999999.99");

    private final LedgerServerCostRepository ledgerServerCostRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;

    public LedgerServerCostAdminService(
        LedgerServerCostRepository ledgerServerCostRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.ledgerServerCostRepository = ledgerServerCostRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional
    public LedgerServerCostResponse createEntry(
        Long groupId,
        LedgerServerCostRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireSuperAdmin(actorEmail);

        LedgerServerCost entry = new LedgerServerCost();
        entry.setGroupId(groupId);
        applyRequest(entry, request);
        entry.setAuthorEmail(normalizeEmail(actorEmail));
        ledgerServerCostRepository.save(entry);

        operationAuditLogService.recordLedgerServerCostAdded(actorEmail, actorNickname, groupId, entry);

        return LedgerServerCostService.toResponse(entry, actorNickname);
    }

    @Transactional
    public LedgerServerCostResponse updateEntry(
        Long groupId,
        Long entryId,
        LedgerServerCostRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireSuperAdmin(actorEmail);

        LedgerServerCost entry = ledgerServerCostRepository.findByIdAndGroupId(entryId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Server cost entry not found"));
        applyRequest(entry, request);
        ledgerServerCostRepository.save(entry);

        operationAuditLogService.recordLedgerServerCostUpdated(actorEmail, actorNickname, groupId, entry);

        String authorNickname = safeTrim(accessControlService.resolveAccessProfile(entry.getAuthorEmail()).nickname());
        return LedgerServerCostService.toResponse(entry, authorNickname.isEmpty() ? null : authorNickname);
    }

    @Transactional
    public void deleteEntry(Long groupId, Long entryId, String actorEmail, String actorNickname) {
        requireSuperAdmin(actorEmail);

        LedgerServerCost entry = ledgerServerCostRepository.findByIdAndGroupId(entryId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Server cost entry not found"));
        String label = entry.getBillingMonth() + " " + entry.getServiceName();
        ledgerServerCostRepository.delete(entry);

        operationAuditLogService.recordLedgerServerCostDeleted(actorEmail, actorNickname, groupId, entryId, label);
    }

    /** Validates the whole request before touching the entry, so a rejected update leaves it unchanged. */
    private void applyRequest(LedgerServerCost entry, LedgerServerCostRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String serviceName = requireServiceName(request.serviceName());
        String billingMonth = requireBillingMonth(request.billingMonth());
        LocalDate chargedDate = requireChargedDate(request.chargedDate());
        BigDecimal usdAmount = normalizeUsdAmount(request.usdAmount());
        Long krwAmount = normalizeKrwAmount(request.krwAmount());
        if (request.reimbursedDate() != null && krwAmount == null) {
            throw new IllegalArgumentException("KRW amount is required once the cost is reimbursed");
        }

        entry.setServiceName(serviceName);
        entry.setBillingMonth(billingMonth);
        entry.setChargedDate(chargedDate);
        entry.setUsdAmount(usdAmount);
        entry.setKrwAmount(krwAmount);
        entry.setPaidBy(normalizeOptional(request.paidBy(), MAX_PAID_BY_LENGTH));
        entry.setReimbursedDate(request.reimbursedDate());
        entry.setMemo(normalizeOptional(request.memo(), MAX_MEMO_LENGTH));
    }

    // Admins can read the ledger; only super admins change it.
    private void requireSuperAdmin(String actorEmail) {
        if (!accessControlService.isSuperAdminEmail(actorEmail)) {
            throw new IllegalArgumentException("Only super admins can manage the ledger");
        }
    }

    private String requireServiceName(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (trimmed.length() > MAX_SERVICE_NAME_LENGTH) {
            throw new IllegalArgumentException("Service name must be " + MAX_SERVICE_NAME_LENGTH + " characters or fewer");
        }
        return trimmed;
    }

    private String requireBillingMonth(String value) {
        String trimmed = safeTrim(value);
        try {
            return YearMonth.parse(trimmed).toString();
        } catch (DateTimeParseException dateTimeParseException) {
            throw new IllegalArgumentException("Billing month must be in YYYY-MM format");
        }
    }

    private LocalDate requireChargedDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("Charged date is required");
        }
        return value;
    }

    private BigDecimal normalizeUsdAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("USD amount must not be negative");
        }
        if (value.compareTo(MAX_USD_AMOUNT) > 0) {
            throw new IllegalArgumentException("USD amount is too large");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private Long normalizeKrwAmount(Long value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new IllegalArgumentException("KRW amount must be positive");
        }
        return value;
    }

    private String normalizeOptional(String value, int maxLength) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
