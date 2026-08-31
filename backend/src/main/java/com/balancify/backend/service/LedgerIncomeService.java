package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerCategoriesResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryResponse;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerIncomeService {

    private final LedgerIncomeEntryRepository ledgerIncomeEntryRepository;
    private final AccessControlService accessControlService;

    public LedgerIncomeService(
        LedgerIncomeEntryRepository ledgerIncomeEntryRepository,
        AccessControlService accessControlService
    ) {
        this.ledgerIncomeEntryRepository = ledgerIncomeEntryRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<LedgerIncomeEntryResponse> getEntries(Long groupId) {
        List<LedgerIncomeEntry> entries = ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateDescIdDesc(groupId);
        Map<String, String> nicknameByEmail = loadAuthorNicknamesByEmail(entries);
        return entries.stream().map(entry -> toResponse(entry, nicknameByEmail)).toList();
    }

    @Transactional(readOnly = true)
    public LedgerCategoriesResponse getCategories(Long groupId) {
        return new LedgerCategoriesResponse(ledgerIncomeEntryRepository.findDistinctCategoriesByGroupId(groupId));
    }

    private Map<String, String> loadAuthorNicknamesByEmail(List<LedgerIncomeEntry> entries) {
        Set<String> emails = new LinkedHashSet<>();
        for (LedgerIncomeEntry entry : entries) {
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

    private LedgerIncomeEntryResponse toResponse(LedgerIncomeEntry entry, Map<String, String> nicknameByEmail) {
        String email = safeTrim(entry.getAuthorEmail()).toLowerCase(Locale.ROOT);
        return new LedgerIncomeEntryResponse(
            entry.getId(),
            entry.getEntryDate(),
            entry.getCategory(),
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
