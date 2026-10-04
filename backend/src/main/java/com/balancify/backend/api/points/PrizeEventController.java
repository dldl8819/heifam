package com.balancify.backend.api.points;

import com.balancify.backend.api.points.dto.PrizeEventConfirmRequest;
import com.balancify.backend.api.points.dto.PrizeEventCreateRequest;
import com.balancify.backend.api.points.dto.PrizeEventListResponse;
import com.balancify.backend.api.points.dto.PrizeEventResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PointService;
import com.balancify.backend.service.PrizeEventService;
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

/** Prize events are seen by whoever can use points; only super admins create, confirm or cancel them. */
@RestController
@RequestMapping("/api/groups/{groupId}/prize-events")
public class PrizeEventController {

    private final PrizeEventService prizeEventService;
    private final PointService pointService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public PrizeEventController(
        PrizeEventService prizeEventService,
        PointService pointService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.prizeEventService = prizeEventService;
        this.pointService = pointService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public PrizeEventListResponse list(@PathVariable Long groupId, HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        if (!pointService.canUsePoints(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Points are not open to this account yet");
        }
        return prizeEventService.list(groupId);
    }

    @PostMapping
    public PrizeEventResponse create(
        @PathVariable Long groupId,
        @RequestBody PrizeEventCreateRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireSuperAdmin(request);
        return handle(() -> prizeEventService.create(groupId, requestBody, requestEmail, resolveNickname(requestEmail)));
    }

    @PostMapping("/{eventId}/confirm")
    public PrizeEventResponse confirm(
        @PathVariable Long groupId,
        @PathVariable Long eventId,
        @RequestBody PrizeEventConfirmRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireSuperAdmin(request);
        return handle(() -> prizeEventService.confirm(
            groupId,
            eventId,
            requestBody,
            requestEmail,
            resolveNickname(requestEmail)
        ));
    }

    @PostMapping("/{eventId}/cancel")
    public PrizeEventResponse cancel(
        @PathVariable Long groupId,
        @PathVariable Long eventId,
        HttpServletRequest request
    ) {
        String requestEmail = requireSuperAdmin(request);
        return handle(() -> prizeEventService.cancel(groupId, eventId, requestEmail, resolveNickname(requestEmail)));
    }

    private String requireSuperAdmin(HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        if (!accessControlService.isSuperAdminEmail(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin role required");
        }
        return requestEmail;
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
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
