package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.LedgerExpenseEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryResponse;
import com.balancify.backend.api.group.dto.LedgerExpenseEntryUpdateRequest;
import com.balancify.backend.api.group.dto.LedgerImportRequest;
import com.balancify.backend.api.group.dto.LedgerImportResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryCreateRequest;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryResponse;
import com.balancify.backend.api.group.dto.LedgerIncomeEntryUpdateRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.LedgerExpenseAdminService;
import com.balancify.backend.service.LedgerIncomeAdminService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupLedgerAdminController {

    private final LedgerIncomeAdminService ledgerIncomeAdminService;
    private final LedgerExpenseAdminService ledgerExpenseAdminService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupLedgerAdminController(
        LedgerIncomeAdminService ledgerIncomeAdminService,
        LedgerExpenseAdminService ledgerExpenseAdminService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.ledgerIncomeAdminService = ledgerIncomeAdminService;
        this.ledgerExpenseAdminService = ledgerExpenseAdminService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @PostMapping("/{groupId}/ledger/income")
    public LedgerIncomeEntryResponse createIncomeEntry(
        @PathVariable Long groupId,
        @RequestBody LedgerIncomeEntryCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            return ledgerIncomeAdminService.createEntry(groupId, request, requestEmail, resolveActorNickname(requestEmail));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw badRequest(illegalArgumentException);
        }
    }

    @PutMapping("/{groupId}/ledger/income/{entryId}")
    public LedgerIncomeEntryResponse updateIncomeEntry(
        @PathVariable Long groupId,
        @PathVariable Long entryId,
        @RequestBody LedgerIncomeEntryUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            return ledgerIncomeAdminService.updateEntry(
                groupId, entryId, request, requestEmail, resolveActorNickname(requestEmail)
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw badRequest(illegalArgumentException);
        } catch (NoSuchElementException noSuchElementException) {
            throw notFound(noSuchElementException);
        }
    }

    @DeleteMapping("/{groupId}/ledger/income/{entryId}")
    public void deleteIncomeEntry(@PathVariable Long groupId, @PathVariable Long entryId, HttpServletRequest httpRequest) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            ledgerIncomeAdminService.deleteEntry(groupId, entryId, requestEmail, resolveActorNickname(requestEmail));
        } catch (NoSuchElementException noSuchElementException) {
            throw notFound(noSuchElementException);
        }
    }

    @PostMapping("/{groupId}/ledger/income/import")
    public LedgerImportResponse importIncomeEntries(
        @PathVariable Long groupId,
        @RequestBody LedgerImportRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            return ledgerIncomeAdminService.importEntries(groupId, request, requestEmail, resolveActorNickname(requestEmail));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw badRequest(illegalArgumentException);
        }
    }

    @PostMapping("/{groupId}/ledger/expense")
    public LedgerExpenseEntryResponse createExpenseEntry(
        @PathVariable Long groupId,
        @RequestBody LedgerExpenseEntryCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            return ledgerExpenseAdminService.createEntry(groupId, request, requestEmail, resolveActorNickname(requestEmail));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw badRequest(illegalArgumentException);
        }
    }

    @PutMapping("/{groupId}/ledger/expense/{entryId}")
    public LedgerExpenseEntryResponse updateExpenseEntry(
        @PathVariable Long groupId,
        @PathVariable Long entryId,
        @RequestBody LedgerExpenseEntryUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            return ledgerExpenseAdminService.updateEntry(
                groupId, entryId, request, requestEmail, resolveActorNickname(requestEmail)
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw badRequest(illegalArgumentException);
        } catch (NoSuchElementException noSuchElementException) {
            throw notFound(noSuchElementException);
        }
    }

    @DeleteMapping("/{groupId}/ledger/expense/{entryId}")
    public void deleteExpenseEntry(@PathVariable Long groupId, @PathVariable Long entryId, HttpServletRequest httpRequest) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            ledgerExpenseAdminService.deleteEntry(groupId, entryId, requestEmail, resolveActorNickname(requestEmail));
        } catch (NoSuchElementException noSuchElementException) {
            throw notFound(noSuchElementException);
        }
    }

    @PostMapping("/{groupId}/ledger/expense/import")
    public LedgerImportResponse importExpenseEntries(
        @PathVariable Long groupId,
        @RequestBody LedgerImportRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);
        try {
            return ledgerExpenseAdminService.importEntries(groupId, request, requestEmail, resolveActorNickname(requestEmail));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw badRequest(illegalArgumentException);
        }
    }

    private ResponseStatusException badRequest(IllegalArgumentException illegalArgumentException) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, illegalArgumentException.getMessage(), illegalArgumentException);
    }

    private ResponseStatusException notFound(NoSuchElementException noSuchElementException) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, noSuchElementException.getMessage(), noSuchElementException);
    }

    private void requireAdmin(String email) {
        if (!accessControlService.isAdminEmail(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return requestEmail;
    }

    private String resolveActorNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
