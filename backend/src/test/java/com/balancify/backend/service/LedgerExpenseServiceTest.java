package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
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
class LedgerExpenseServiceTest {

    @Mock
    private LedgerExpenseEntryRepository ledgerExpenseEntryRepository;

    @Mock
    private AccessControlService accessControlService;

    private LedgerExpenseService ledgerExpenseService;

    @BeforeEach
    void setUp() {
        ledgerExpenseService = new LedgerExpenseService(ledgerExpenseEntryRepository, accessControlService);
    }

    private LedgerExpenseEntry entry(Long id, String expenseType, String authorEmail) {
        LedgerExpenseEntry entry = new LedgerExpenseEntry();
        entry.setId(id);
        entry.setGroupId(1L);
        entry.setEntryDate(LocalDate.of(2026, 1, 5));
        entry.setExpenseType(expenseType);
        entry.setCategory("YOUR_CATEGORY");
        entry.setTarget("YOUR_TARGET");
        entry.setAmount(5000L);
        entry.setAuthorEmail(authorEmail);
        return entry;
    }

    @Test
    void returnsAllEntriesWhenExpenseTypeFilterIsBlank() {
        when(ledgerExpenseEntryRepository.findByGroupIdOrderByEntryDateDescIdDesc(1L))
            .thenReturn(List.of(entry(1L, "FIXED", "ops@hei.gg")));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        List<LedgerExpenseEntryResponse> responses = ledgerExpenseService.getEntries(1L, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).expenseType()).isEqualTo("FIXED");
    }

    @Test
    void filtersByExpenseTypeWhenProvided() {
        when(ledgerExpenseEntryRepository.findByGroupIdAndExpenseTypeOrderByEntryDateDescIdDesc(1L, "VARIABLE"))
            .thenReturn(List.of(entry(2L, "VARIABLE", "ops@hei.gg")));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        List<LedgerExpenseEntryResponse> responses = ledgerExpenseService.getEntries(1L, "variable");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).expenseType()).isEqualTo("VARIABLE");
        verify(ledgerExpenseEntryRepository).findByGroupIdAndExpenseTypeOrderByEntryDateDescIdDesc(1L, "VARIABLE");
    }

    @Test
    void defaultsCategoryLookupToFixedWhenExpenseTypeIsBlank() {
        when(ledgerExpenseEntryRepository.findDistinctCategoriesByGroupIdAndExpenseType(1L, "FIXED"))
            .thenReturn(List.of("YOUR_CATEGORY"));

        assertThat(ledgerExpenseService.getCategories(1L, null).categories()).containsExactly("YOUR_CATEGORY");
    }
}
