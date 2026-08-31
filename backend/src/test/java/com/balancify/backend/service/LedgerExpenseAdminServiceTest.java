package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.group.dto.LedgerImportRequest;
import com.balancify.backend.api.group.dto.LedgerImportResponse;
import com.balancify.backend.domain.LedgerExpenseEntry;
import com.balancify.backend.repository.LedgerExpenseEntryRepository;
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
class LedgerExpenseAdminServiceTest {

    @Mock
    private LedgerExpenseEntryRepository ledgerExpenseEntryRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private LedgerExpenseAdminService ledgerExpenseAdminService;

    @BeforeEach
    void setUp() {
        ledgerExpenseAdminService = new LedgerExpenseAdminService(
            ledgerExpenseEntryRepository, accessControlService, operationAuditLogService
        );
        when(accessControlService.isAdminEmail("ops@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);
        when(ledgerExpenseEntryRepository.save(any(LedgerExpenseEntry.class))).thenAnswer(invocation -> {
            LedgerExpenseEntry entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(1L);
            }
            return entry;
        });
    }

    @Test
    void createsFixedExpenseEntryForAdmin() {
        LedgerExpenseEntryResponse response = ledgerExpenseAdminService.createEntry(
            1L,
            new LedgerExpenseEntryCreateRequest(
                LocalDate.of(2026, 1, 5), "fixed", "YOUR_CATEGORY", "YOUR_PAYMENT_METHOD", 5000L, "YOUR_MEMO"
            ),
            "ops@hei.gg",
            "OpsUser"
        );

        assertThat(response.expenseType()).isEqualTo("FIXED");
        assertThat(response.target()).isEqualTo("YOUR_PAYMENT_METHOD");
        verify(operationAuditLogService).recordLedgerExpenseAdded(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), any());
    }

    @Test
    void rejectsInvalidExpenseType() {
        assertThatThrownBy(() ->
            ledgerExpenseAdminService.createEntry(
                1L,
                new LedgerExpenseEntryCreateRequest(
                    LocalDate.of(2026, 1, 5), "UNKNOWN", "YOUR_CATEGORY", "YOUR_TARGET", 5000L, null
                ),
                "ops@hei.gg",
                "OpsUser"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expense type must be FIXED or VARIABLE");
    }

    @Test
    void rejectsCreateWhenActorIsNotAdmin() {
        assertThatThrownBy(() ->
            ledgerExpenseAdminService.createEntry(
                1L,
                new LedgerExpenseEntryCreateRequest(
                    LocalDate.of(2026, 1, 5), "VARIABLE", "YOUR_CATEGORY", "YOUR_TARGET", 5000L, null
                ),
                "member@hei.gg",
                "Member"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only admins can manage the ledger");

        verify(ledgerExpenseEntryRepository, never()).save(any(LedgerExpenseEntry.class));
    }

    @Test
    void throwsWhenDeletingMissingEntry() {
        when(ledgerExpenseEntryRepository.findByIdAndGroupId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerExpenseAdminService.deleteEntry(1L, 99L, "ops@hei.gg", "OpsUser"))
            .isInstanceOf(java.util.NoSuchElementException.class)
            .hasMessage("Ledger expense entry not found");
    }

    @Test
    void importsValidRowsWithSharedExpenseTypeAndSkipsInvalidOnes() {
        String csv = "date,amount,category,target,memo\n"
            + "2026-01-05,5000,YOUR_CATEGORY,YOUR_TARGET_A,YOUR_MEMO\n"
            + "2026-01-06,not-a-number,YOUR_CATEGORY,YOUR_TARGET_B,YOUR_MEMO\n"
            + "2026-01-07,7000,YOUR_CATEGORY,YOUR_TARGET_C,YOUR_MEMO\n";

        LedgerImportResponse response = ledgerExpenseAdminService.importEntries(
            1L, new LedgerImportRequest(csv, "variable"), "ops@hei.gg", "OpsUser"
        );

        assertThat(response.importedCount()).isEqualTo(2);
        assertThat(response.skippedRows()).hasSize(1);
        assertThat(response.skippedRows().get(0).rowNumber()).isEqualTo(3);
        verify(operationAuditLogService).recordLedgerExpenseImported(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), eq(2));
    }

    @Test
    void rejectsImportWithoutValidExpenseType() {
        assertThatThrownBy(() ->
            ledgerExpenseAdminService.importEntries(
                1L, new LedgerImportRequest("date,amount,category,target,memo\n", null), "ops@hei.gg", "OpsUser"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expense type must be FIXED or VARIABLE");
    }
}
