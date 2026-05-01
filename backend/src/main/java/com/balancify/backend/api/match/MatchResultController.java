package com.balancify.backend.api.match;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.match.dto.ManualMatchCreateRequest;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.ManualMatchService;
import com.balancify.backend.service.MatchResultService;
import com.balancify.backend.service.exception.MatchConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matches")
public class MatchResultController {

    private final MatchResultService matchResultService;
    private final ManualMatchService manualMatchService;
    private final MmrAccessRequestResolver mmrAccessRequestResolver;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;
    private final AccessControlService accessControlService;

    public MatchResultController(
        MatchResultService matchResultService,
        ManualMatchService manualMatchService,
        MmrAccessRequestResolver mmrAccessRequestResolver,
        AuthenticatedRequestResolver authenticatedRequestResolver,
        AccessControlService accessControlService
    ) {
        this.matchResultService = matchResultService;
        this.manualMatchService = manualMatchService;
        this.mmrAccessRequestResolver = mmrAccessRequestResolver;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
        this.accessControlService = accessControlService;
    }

    @PostMapping("/manual")
    public MatchResultResponse createManualMatch(
        @RequestBody ManualMatchCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        try {
            MatchResultResponse response = manualMatchService.createManualMatch(
                request,
                extractRequestEmail(httpRequest),
                resolveRecordedByNickname(httpRequest)
            );
            if (mmrAccessRequestResolver.canViewMmr(httpRequest)) {
                return response;
            }

            return MmrMaskingMapper.maskMatchResult(response);
        } catch (MatchConflictException | ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/{id}/result")
    public MatchResultResponse processResult(
        @PathVariable("id") Long matchId,
        @RequestBody MatchResultRequest request,
        HttpServletRequest httpRequest
    ) {
        try {
            MatchResultResponse response = matchResultService.processMatchResult(
                matchId,
                request,
                extractRequestEmail(httpRequest),
                resolveRecordedByNickname(httpRequest),
                false
            );
            if (mmrAccessRequestResolver.canViewMmr(httpRequest)) {
                return response;
            }

            return MmrMaskingMapper.maskMatchResult(response);
        } catch (MatchConflictException | ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/{id}/result")
    public MatchResultResponse updateResult(
        @PathVariable("id") Long matchId,
        @RequestBody MatchResultRequest request,
        HttpServletRequest httpRequest
    ) {
        try {
            MatchResultResponse response = matchResultService.processMatchResult(
                matchId,
                request,
                extractRequestEmail(httpRequest),
                resolveRecordedByNickname(httpRequest),
                true
            );
            if (mmrAccessRequestResolver.canViewMmr(httpRequest)) {
                return response;
            }

            return MmrMaskingMapper.maskMatchResult(response);
        } catch (MatchConflictException | ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void deleteMatch(@PathVariable("id") Long matchId) {
        try {
            matchResultService.deleteMatch(matchId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    private String extractRequestEmail(HttpServletRequest request) {
        String value = authenticatedRequestResolver.resolve(request).email();
        if (value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String resolveRecordedByNickname(HttpServletRequest request) {
        String email = extractRequestEmail(request);
        if (email == null || email.isBlank()) {
            return null;
        }

        String value = accessControlService.resolveAccessProfile(email).nickname();
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
