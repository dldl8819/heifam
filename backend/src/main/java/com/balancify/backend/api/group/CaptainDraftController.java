package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
import com.balancify.backend.service.CaptainDraftService;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class CaptainDraftController {

    private final CaptainDraftService captainDraftService;

    public CaptainDraftController(CaptainDraftService captainDraftService) {
        this.captainDraftService = captainDraftService;
    }

    @PostMapping("/{groupId}/captain-drafts")
    public CaptainDraftResponse createDraft(
        @PathVariable Long groupId,
        @RequestBody CaptainDraftCreateRequest request
    ) {
        try {
            return captainDraftService.createDraft(groupId, request);
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

    @PutMapping("/{groupId}/captain-drafts/{draftId}/entries")
    public CaptainDraftResponse updateEntries(
        @PathVariable Long groupId,
        @PathVariable Long draftId,
        @RequestBody CaptainDraftEntriesUpdateRequest request
    ) {
        try {
            return captainDraftService.updateEntries(groupId, draftId, request);
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
}
