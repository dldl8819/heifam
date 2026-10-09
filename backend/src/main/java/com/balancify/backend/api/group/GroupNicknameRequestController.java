package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.NicknameRequestCreateRequest;
import com.balancify.backend.api.group.dto.NicknameRequestDecisionRequest;
import com.balancify.backend.api.group.dto.NicknameRequestListResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.NicknameRequestService;
import com.balancify.backend.service.exception.BoardLimitException;
import com.balancify.backend.service.exception.NicknameRequestConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Requests for a nickname change. Members file, read and call off their own (ServiceAccessFilter);
 * deciding one is for admins (AdminKeyFilter, and NicknameRequestService checks again).
 */
@RestController
@RequestMapping("/api/groups/{groupId}/nickname-requests")
public class GroupNicknameRequestController {

    private final NicknameRequestService nicknameRequestService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupNicknameRequestController(
        NicknameRequestService nicknameRequestService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.nicknameRequestService = nicknameRequestService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public NicknameRequestListResponse list(@PathVariable Long groupId, HttpServletRequest request) {
        String email = requireRequestEmail(request);
        return handle(() -> nicknameRequestService.list(groupId, email));
    }

    @PostMapping
    public NicknameRequestListResponse create(
        @PathVariable Long groupId,
        @RequestBody NicknameRequestCreateRequest body,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> nicknameRequestService.create(
            groupId,
            email,
            body == null ? null : body.desiredNickname(),
            body == null ? null : body.reason()
        ));
    }

    @PostMapping("/{requestId}/cancel")
    public NicknameRequestListResponse cancel(
        @PathVariable Long groupId,
        @PathVariable Long requestId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> nicknameRequestService.cancel(groupId, requestId, email));
    }

    @PutMapping("/{requestId}/decision")
    public NicknameRequestListResponse decide(
        @PathVariable Long groupId,
        @PathVariable Long requestId,
        @RequestBody NicknameRequestDecisionRequest body,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> nicknameRequestService.decide(
            groupId,
            requestId,
            email,
            body == null ? null : body.status(),
            body == null ? null : body.note()
        ));
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return requestEmail;
    }

    private <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NicknameRequestConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (BoardLimitException exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
