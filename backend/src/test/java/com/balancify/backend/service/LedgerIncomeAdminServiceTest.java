package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerImportRequest;
import com.balancify.backend.api.group.dto.LedgerImportResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryUpdateRequest;
import com.balancify.backend.domain.LedgerIncomeEntry;
import com.balancify.backend.repository.LedgerIncomeEntryRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerIncomeAdminServiceTest {

    @Mock
    private LedgerIncomeEntryRepository ledgerIncomeEntryRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private LedgerIncomeAdminService ledgerIncomeAdminService;

    @BeforeEach
    void setUp() {
        ledgerIncomeAdminService = new LedgerIncomeAdminService(
            ledgerIncomeEntryRepository, accessControlService, operationAuditLogService
        );
        when(accessControlService.isAdminEmail("ops@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);
        when(ledgerIncomeEntryRepository.save(any(LedgerIncomeEntry.class))).thenAnswer(invocation -> {
            LedgerIncomeEntry entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(1L);
            }
            return entry;
        });
    }

    @Test
    void createsEntryForAdmin() {
        LedgerIncomeEntryResponse response = ledgerIncomeAdminService.createEntry(
            1L,
            new LedgerIncomeEntryCreateRequest(LocalDate.of(2026, 1, 5), "YOUR_CATEGORY", 10000L, "YOUR_MEMO"),
            "ops@hei.gg",
            "OpsUser"
        );

        assertThat(response.category()).isEqualTo("YOUR_CATEGORY");
        assertThat(response.amount()).isEqualTo(10000L);
        assertThat(response.authorNickname()).isEqualTo("OpsUser");
        verify(operationAuditLogService).recordLedgerIncomeAdded(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), any());
    }

    @Test
    void rejectsCreateWhenActorIsNotAdmin() {
        assertThatThrownBy(() ->
            ledgerIncomeAdminService.createEntry(
                1L,
                new LedgerIncomeEntryCreateRequest(LocalDate.of(2026, 1, 5), "YOUR_CATEGORY", 10000L, null),
                "member@hei.gg",
                "Member"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only admins can manage the ledger");

        verify(ledgerIncomeEntryRepository, never()).save(any(LedgerIncomeEntry.class));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() ->
            ledgerIncomeAdminService.createEntry(
                1L,
                new LedgerIncomeEntryCreateRequest(LocalDate.of(2026, 1, 5), "YOUR_CATEGORY", 0L, null),
                "ops@hei.gg",
                "OpsUser"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Amount must be a positive number");
    }

    @Test
    void deletesExistingEntry() {
        LedgerIncomeEntry entry = new LedgerIncomeEntry();
        entry.setId(7L);
        entry.setGroupId(1L);
        entry.setEntryDate(LocalDate.of(2026, 1, 5));
        entry.setCategory("YOUR_CATEGORY");
        when(ledgerIncomeEntryRepository.findByIdAndGroupId(7L, 1L)).thenReturn(Optional.of(entry));

        ledgerIncomeAdminService.deleteEntry(1L, 7L, "ops@hei.gg", "OpsUser");

        verify(ledgerIncomeEntryRepository).delete(entry);
        verify(operationAuditLogService)
            .recordLedgerIncomeDeleted(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), eq(7L), any());
    }

    @Test
    void throwsWhenUpdatingMissingEntry() {
        when(ledgerIncomeEntryRepository.findByIdAndGroupId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            ledgerIncomeAdminService.updateEntry(
                1L,
                99L,
                new LedgerIncomeEntryUpdateRequest(LocalDate.of(2026, 1, 5), "YOUR_CATEGORY", 1000L, null),
                "ops@hei.gg",
                "OpsUser"
            )
        )
            .isInstanceOf(java.util.NoSuchElementException.class)
            .hasMessage("Ledger income entry not found");
    }

    @Test
    void importsValidRowsAndSkipsInvalidOnes() {
        String csv = "date,amount,category,memo\n"
            + "2026-01-05,10000,YOUR_CATEGORY,YOUR_MEMO\n"
            + "not-a-date,10000,YOUR_CATEGORY,YOUR_MEMO\n"
            + "2026-01-06,0,YOUR_CATEGORY,YOUR_MEMO\n"
            + "2026-01-07,20000,,YOUR_MEMO\n";

        LedgerImportResponse response = ledgerIncomeAdminService.importEntries(
            1L, new LedgerImportRequest(csv, null), "ops@hei.gg", "OpsUser"
        );

        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.skippedRows()).hasSize(3);
        assertThat(response.skippedRows().get(0).rowNumber()).isEqualTo(3);
        assertThat(response.skippedRows().get(1).rowNumber()).isEqualTo(4);
        assertThat(response.skippedRows().get(2).rowNumber()).isEqualTo(5);
        verify(operationAuditLogService).recordLedgerIncomeImported(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), eq(1));
    }

    @Test
    void rejectsImportWhenActorIsNotAdmin() {
        assertThatThrownBy(() ->
            ledgerIncomeAdminService.importEntries(
                1L, new LedgerImportRequest("date,amount,category,memo\n", null), "member@hei.gg", "Member"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only admins can manage the ledger");

        verify(ledgerIncomeEntryRepository, never()).save(any(LedgerIncomeEntry.class));
    }
}
