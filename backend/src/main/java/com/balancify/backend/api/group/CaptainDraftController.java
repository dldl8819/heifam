package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftListResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
import com.balancify.backend.api.group.dto.CaptainDraftUnpickRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.CaptainDraftService;
import com.balancify.backend.service.exception.CaptainDraftResultLockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The regular draft (정기 감전), open to members with access (AdminKeyFilter: SERVICE_ACCESS): they
 * read it, start one, put players in teams, take a pick back before any result, and save the sets
 * with a set's first result. Changing a result already in is for admins and match result editors
 * (below and in CaptainDraftService); removing a draft is for admins (AdminKeyFilter).
 */
@RestController
@RequestMapping("/api/groups")
public class CaptainDraftController {

    private final CaptainDraftService captainDraftService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public CaptainDraftController(
        CaptainDraftService captainDraftService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.captainDraftService = captainDraftService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @PostMapping("/{groupId}/captain-drafts")
    public CaptainDraftResponse createDraft(
        @PathVariable Long groupId,
        @RequestBody CaptainDraftCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        String email = requireRequestEmail(httpRequest);
        try {
            return captainDraftService.createDraft(groupId, request, email, recorderNickname(email));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    @GetMapping("/{groupId}/captain-drafts")
    public CaptainDraftListResponse listDrafts(@PathVariable Long groupId) {
        return captainDraftService.listDrafts(groupId);
    }

    @GetMapping("/{groupId}/captain-drafts/latest")
    public CaptainDraftResponse getLatestDraft(@PathVariable Long groupId) {
        try {
            return captainDraftService.getLatestDraft(groupId);
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    @GetMapping("/{groupId}/captain-drafts/{draftId}")
    public CaptainDraftResponse getDraft(
        @PathVariable Long groupId,
        @PathVariable Long draftId
    ) {
        try {
            return captainDraftService.getDraft(groupId, draftId);
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    // Admins only (AdminKeyFilter); the operation log names who removed it.
    @DeleteMapping("/{groupId}/captain-drafts/{draftId}")
    public CaptainDraftListResponse deleteDraft(
        @PathVariable Long groupId,
        @PathVariable Long draftId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        try {
            return captainDraftService.deleteDraft(groupId, draftId, email, accessControlService.resolveDisplayNickname(email));
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    @PostMapping("/{groupId}/captain-drafts/{draftId}/pick")
    public CaptainDraftResponse pickPlayer(
        @PathVariable Long groupId,
        @PathVariable Long draftId,
        @RequestBody CaptainDraftPickRequest request
    ) {
        try {
            return captainDraftService.pickPlayer(groupId, draftId, request);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    @PostMapping("/{groupId}/captain-drafts/{draftId}/unpick")
    public CaptainDraftResponse unpickPlayer(
        @PathVariable Long groupId,
        @PathVariable Long draftId,
        @RequestBody CaptainDraftUnpickRequest request
    ) {
        try {
            return captainDraftService.unpickPlayer(groupId, draftId, request);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        } catch (IllegalStateException resultsEntered) {
            // A draft whose games have begun keeps its teams, for everyone.
            throw new ResponseStatusException(HttpStatus.CONFLICT, resultsEntered.getMessage(), resultsEntered);
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    // Members save the sets and a set's first result; changing a result that is in already is for
    // admins and match result editors, decided here and held to in CaptainDraftService.
    @PutMapping("/{groupId}/captain-drafts/{draftId}/entries")
    public CaptainDraftResponse updateEntries(
        @PathVariable Long groupId,
        @PathVariable Long draftId,
        @RequestBody CaptainDraftEntriesUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        String email = requireRequestEmail(httpRequest);
        boolean canEditResults = accessControlService.isAdminEmail(email) || accessControlService.isMatchResultEditor(email);
        try {
            return captainDraftService.updateEntries(groupId, draftId, request, canEditResults, email, recorderNickname(email));
        } catch (CaptainDraftResultLockedException resultLocked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, resultLocked.getMessage(), resultLocked);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    // The name a match result shows for whoever entered it (MatchResultController), for the same here.
    private String recorderNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String email = authenticatedRequestResolver.resolve(request).email();
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return email;
    }
}
