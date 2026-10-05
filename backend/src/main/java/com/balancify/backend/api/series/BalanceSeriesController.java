package com.balancify.backend.api.series;

import com.balancify.backend.api.series.dto.BalanceSeriesListResponse;
import com.balancify.backend.api.series.dto.StartBalanceSeriesRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.BalanceSeriesService;
import com.balancify.backend.service.exception.MatchConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Series played after a multi-balance. Members list and start them (ServiceAccessFilter keeps the
 * rest out); cancelling one is for admins. Games are recorded through the usual match result API.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/balance-series")
public class BalanceSeriesController {

    private final BalanceSeriesService balanceSeriesService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;
    private final MmrAccessRequestResolver mmrAccessRequestResolver;

    public BalanceSeriesController(
        BalanceSeriesService balanceSeriesService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver,
        MmrAccessRequestResolver mmrAccessRequestResolver
    ) {
        this.balanceSeriesService = balanceSeriesService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
        this.mmrAccessRequestResolver = mmrAccessRequestResolver;
    }

    @GetMapping
    public BalanceSeriesListResponse list(@PathVariable Long groupId, HttpServletRequest request) {
        requireEmail(request);
        return balanceSeriesService.list(groupId, mmrAccessRequestResolver.canViewMmr(request));
    }

    @PostMapping
    public BalanceSeriesListResponse start(
        @PathVariable Long groupId,
        @RequestBody StartBalanceSeriesRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireEmail(request);
        if (requestBody == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return handle(() -> balanceSeriesService.start(
            groupId,
            requestBody.lineups(),
            requestEmail,
            resolveNickname(requestEmail),
            mmrAccessRequestResolver.canViewMmr(request)
        ));
    }

    @PostMapping("/{seriesId}/cancel")
    public BalanceSeriesListResponse cancel(
        @PathVariable Long groupId,
        @PathVariable Long seriesId,
        HttpServletRequest request
    ) {
        String requestEmail = requireAdmin(request);
        return handle(() -> balanceSeriesService.cancel(
            groupId,
            seriesId,
            requestEmail,
            resolveNickname(requestEmail),
            mmrAccessRequestResolver.canViewMmr(request)
        ));
    }

    private String requireEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return requestEmail;
    }

    private String requireAdmin(HttpServletRequest request) {
        String requestEmail = requireEmail(request);
        if (!accessControlService.isAdminEmail(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return requestEmail;
    }

    private String resolveNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }

    private <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (MatchConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
