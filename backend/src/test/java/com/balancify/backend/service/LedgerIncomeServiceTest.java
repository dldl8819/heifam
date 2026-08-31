package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerIncomeEntryResponse;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
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
class LedgerIncomeServiceTest {

    @Mock
    private LedgerIncomeEntryRepository ledgerIncomeEntryRepository;

    @Mock
    private AccessControlService accessControlService;

    private LedgerIncomeService ledgerIncomeService;

    @BeforeEach
    void setUp() {
        ledgerIncomeService = new LedgerIncomeService(ledgerIncomeEntryRepository, accessControlService);
    }

    private LedgerIncomeEntry entry(Long id, String authorEmail) {
        LedgerIncomeEntry entry = new LedgerIncomeEntry();
        entry.setId(id);
        entry.setGroupId(1L);
        entry.setEntryDate(LocalDate.of(2026, 1, 5));
        entry.setCategory("YOUR_CATEGORY");
        entry.setAmount(10000L);
        entry.setMemo("YOUR_MEMO");
        entry.setAuthorEmail(authorEmail);
        return entry;
    }

    @Test
    void resolvesAuthorNicknameForEachDistinctEmailOnce() {
        when(ledgerIncomeEntryRepository.findByGroupIdOrderByEntryDateDescIdDesc(1L))
            .thenReturn(List.of(entry(1L, "ops@hei.gg"), entry(2L, "ops@hei.gg")));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        List<LedgerIncomeEntryResponse> responses = ledgerIncomeService.getEntries(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).authorNickname()).isEqualTo("OpsUser");
        assertThat(responses.get(1).authorNickname()).isEqualTo("OpsUser");
        verify(accessControlService, times(1)).resolveAccessProfile("ops@hei.gg");
    }

    @Test
    void returnsDistinctCategories() {
        when(ledgerIncomeEntryRepository.findDistinctCategoriesByGroupId(1L))
            .thenReturn(List.of("YOUR_CATEGORY_A", "YOUR_CATEGORY_B"));

        assertThat(ledgerIncomeService.getCategories(1L).categories())
            .containsExactly("YOUR_CATEGORY_A", "YOUR_CATEGORY_B");
    }
}
