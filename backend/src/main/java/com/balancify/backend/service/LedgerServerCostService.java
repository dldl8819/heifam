package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.LedgerServerCostResponse;
import com.balancify.backend.domain.LedgerServerCost;
import com.balancify.backend.repository.LedgerServerCostRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServerCostService {

    private final LedgerServerCostRepository ledgerServerCostRepository;
    private final AccessControlService accessControlService;

    public LedgerServerCostService(
        LedgerServerCostRepository ledgerServerCostRepository,
        AccessControlService accessControlService
    ) {
        this.ledgerServerCostRepository = ledgerServerCostRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<LedgerServerCostResponse> getEntries(Long groupId) {
        List<LedgerServerCost> entries = ledgerServerCostRepository.findByGroupIdOrderByBillingMonthDescIdDesc(groupId);

        Set<String> emails = new LinkedHashSet<>();
        for (LedgerServerCost entry : entries) {
            String email = normalizeEmail(entry.getAuthorEmail());
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

        return entries.stream()
            .map(entry -> toResponse(entry, nicknameByEmail.get(normalizeEmail(entry.getAuthorEmail()))))
            .toList();
    }

    static LedgerServerCostResponse toResponse(LedgerServerCost entry, String authorNickname) {
        return new LedgerServerCostResponse(
            entry.getId(),
            entry.getServiceName(),
            entry.getBillingMonth(),
            entry.getChargedDate(),
            entry.getUsdAmount(),
            entry.getKrwAmount(),
            entry.getPaidBy(),
            entry.getReimbursedDate(),
            entry.getMemo(),
            authorNickname,
            entry.getCreatedAt()
        );
    }

    private static String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
