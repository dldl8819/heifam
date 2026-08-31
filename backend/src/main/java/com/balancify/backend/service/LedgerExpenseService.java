package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerCategoriesResponse;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LedgerExpenseService {

    private final LedgerExpenseEntryRepository ledgerExpenseEntryRepository;
    private final AccessControlService accessControlService;

    public LedgerExpenseService(
        LedgerExpenseEntryRepository ledgerExpenseEntryRepository,
        AccessControlService accessControlService
    ) {
        this.ledgerExpenseEntryRepository = ledgerExpenseEntryRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<LedgerExpenseEntryResponse> getEntries(Long groupId, String expenseType) {
        List<LedgerExpenseEntry> entries = StringUtils.hasText(expenseType)
            ? ledgerExpenseEntryRepository.findByGroupIdAndExpenseTypeOrderByEntryDateDescIdDesc(
                groupId,
                expenseType.trim().toUpperCase(Locale.ROOT)
            )
            : ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateDescIdDesc(groupId);
        Map<String, String> nicknameByEmail = loadAuthorNicknamesByEmail(entries);
        return entries.stream().map(entry -> toResponse(entry, nicknameByEmail)).toList();
    }

    @Transactional(readOnly = true)
    public LedgerCategoriesResponse getCategories(Long groupId, String expenseType) {
        String normalizedType = StringUtils.hasText(expenseType) ? expenseType.trim().toUpperCase(Locale.ROOT) : "FIXED";
        return new LedgerCategoriesResponse(
            ledgerExpenseEntryRepository.findDistinctCategoriesByGroupIdAndExpenseType(groupId, normalizedType)
        );
    }

    private Map<String, String> loadAuthorNicknamesByEmail(List<LedgerExpenseEntry> entries) {
        Set<String> emails = new LinkedHashSet<>();
        for (LedgerExpenseEntry entry : entries) {
            String email = safeTrim(entry.getAuthorEmail()).toLowerCase(Locale.ROOT);
            if (!email.isEmpty()) {
                emails.add(email);
            }
        }

        Map<String, String> nicknameByEmail = new LinkedHashMap<>();
        for (String email : emails) {
            String nickname = safeTrim(accessControlService.resolveAccessProfile(email).nickname());
            if (!nickname.isEmpty()) {
                nicknameByEmail.put(email, nickname);
            }
        }
        return nicknameByEmail;
    }

    private LedgerExpenseEntryResponse toResponse(LedgerExpenseEntry entry, Map<String, String> nicknameByEmail) {
        String email = safeTrim(entry.getAuthorEmail()).toLowerCase(Locale.ROOT);
        return new LedgerExpenseEntryResponse(
            entry.getId(),
            entry.getEntryDate(),
            entry.getExpenseType(),
            entry.getCategory(),
            entry.getTarget(),
            safeAmount(entry.getAmount()),
            entry.getMemo(),
            nicknameByEmail.get(email),
            entry.getCreatedAt()
        );
    }

    private long safeAmount(Long amount) {
        return amount == null ? 0L : amount;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
