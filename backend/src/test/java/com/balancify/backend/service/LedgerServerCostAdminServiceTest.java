package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.LedgerServerCostRequest;
import com.balancify.backend.api.group.dto.LedgerServerCostResponse;
import com.balancify.backend.domain.LedgerServerCost;
import com.balancify.backend.repository.LedgerServerCostRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
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
class LedgerServerCostAdminServiceTest {

    @Mock
    private LedgerServerCostRepository ledgerServerCostRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private LedgerServerCostAdminService ledgerServerCostAdminService;

    @BeforeEach
    void setUp() {
        ledgerServerCostAdminService = new LedgerServerCostAdminService(
            ledgerServerCostRepository, accessControlService, operationAuditLogService
        );
        when(accessControlService.isSuperAdminEmail("superadmin@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("superadmin@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("ops@hei.gg")).thenReturn(true);
        when(accessControlService.resolveAccessProfile(any())).thenReturn(new AccessControlService.AccessProfile(
            "superadmin@hei.gg", "SuperAdmin", "SUPER_ADMIN", true, true, true, true, null
        ));
        when(ledgerServerCostRepository.save(any(LedgerServerCost.class))).thenAnswer(invocation -> {
            LedgerServerCost entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(1L);
            }
            return entry;
        });
    }

    private LedgerServerCostRequest request(String billingMonth, BigDecimal usdAmount, Long krwAmount, LocalDate reimbursedDate) {
        return new LedgerServerCostRequest(
            " Render ",
            billingMonth,
            LocalDate.of(2026, 9, 1),
            usdAmount,
            krwAmount,
            " 개인 카드 ",
            reimbursedDate,
            "  "
        );
    }

    @Test
    void createsServerCostForSuperAdmin() {
        LedgerServerCostResponse response = ledgerServerCostAdminService.createEntry(
            1L,
            request("2026-08", new BigDecimal("7.4"), null, null),
            "superadmin@hei.gg",
            "SuperAdmin"
        );

        assertThat(response.serviceName()).isEqualTo("Render");
        assertThat(response.billingMonth()).isEqualTo("2026-08");
        assertThat(response.usdAmount()).isEqualByComparingTo("7.40");
        assertThat(response.usdAmount().scale()).isEqualTo(2);
        assertThat(response.krwAmount()).isNull();
        assertThat(response.paidBy()).isEqualTo("개인 카드");
        assertThat(response.reimbursedDate()).isNull();
        assertThat(response.memo()).isNull();
        verify(operationAuditLogService).recordLedgerServerCostAdded(eq("superadmin@hei.gg"), eq("SuperAdmin"), eq(1L), any());
    }

    @Test
    void rejectsCreateWhenActorIsAdminButNotSuperAdmin() {
        assertThatThrownBy(() ->
            ledgerServerCostAdminService.createEntry(1L, request("2026-08", null, null, null), "ops@hei.gg", "Ops")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only super admins can manage the ledger");

        verify(ledgerServerCostRepository, never()).save(any(LedgerServerCost.class));
    }

    @Test
    void rejectsInvalidBillingMonth() {
        assertThatThrownBy(() ->
            ledgerServerCostAdminService.createEntry(1L, request("2026-13", null, null, null), "superadmin@hei.gg", "SuperAdmin")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Billing month must be in YYYY-MM format");
    }

    @Test
    void rejectsReimbursementWithoutKrwAmount() {
        assertThatThrownBy(() ->
            ledgerServerCostAdminService.createEntry(
                1L,
                request("2026-08", new BigDecimal("7.41"), null, LocalDate.of(2026, 9, 20)),
                "superadmin@hei.gg",
                "SuperAdmin"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("KRW amount is required once the cost is reimbursed");
    }

    @Test
    void rejectsNonPositiveKrwAmount() {
        assertThatThrownBy(() ->
            ledgerServerCostAdminService.createEntry(1L, request("2026-08", null, 0L, null), "superadmin@hei.gg", "SuperAdmin")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("KRW amount must be positive");
    }

    @Test
    void updatesServerCostWithWonAmountAndReimbursementDate() {
        LedgerServerCost existing = new LedgerServerCost();
        existing.setId(5L);
        existing.setGroupId(1L);
        existing.setServiceName("Render");
        existing.setBillingMonth("2026-08");
        existing.setChargedDate(LocalDate.of(2026, 9, 1));
        existing.setAuthorEmail("superadmin@hei.gg");
        when(ledgerServerCostRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(existing));

        LedgerServerCostResponse response = ledgerServerCostAdminService.updateEntry(
            1L,
            5L,
            request("2026-08", new BigDecimal("7.41"), 10_300L, LocalDate.of(2026, 9, 20)),
            "superadmin@hei.gg",
            "SuperAdmin"
        );

        assertThat(response.krwAmount()).isEqualTo(10_300L);
        assertThat(response.reimbursedDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(response.authorNickname()).isEqualTo("SuperAdmin");
        verify(operationAuditLogService).recordLedgerServerCostUpdated(eq("superadmin@hei.gg"), eq("SuperAdmin"), eq(1L), any());
    }

    @Test
    void throwsWhenDeletingMissingServerCost() {
        when(ledgerServerCostRepository.findByIdAndGroupId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerServerCostAdminService.deleteEntry(1L, 99L, "superadmin@hei.gg", "SuperAdmin"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Server cost entry not found");
    }

    @Test
    void deletesServerCostAndRecordsAudit() {
        LedgerServerCost existing = new LedgerServerCost();
        existing.setId(6L);
        existing.setGroupId(1L);
        existing.setServiceName("Render");
        existing.setBillingMonth("2026-07");
        when(ledgerServerCostRepository.findByIdAndGroupId(6L, 1L)).thenReturn(Optional.of(existing));

        ledgerServerCostAdminService.deleteEntry(1L, 6L, "superadmin@hei.gg", "SuperAdmin");

        verify(ledgerServerCostRepository).delete(existing);
        verify(operationAuditLogService)
            .recordLedgerServerCostDeleted(eq("superadmin@hei.gg"), eq("SuperAdmin"), eq(1L), eq(6L), eq("2026-07 Render"));
    }
}
