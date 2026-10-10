package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.PrizeDrawListResponse;
import com.balancify.backend.api.group.dto.PrizeDrawSaveRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PrizeDrawService;
import com.balancify.backend.service.exception.BoardForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The records of prize draws. Members read them (ServiceAccessFilter); saving one is for admins
 * and removing one for super admins (AdminKeyFilter, and PrizeDrawService checks again).
 */
@RestController
@RequestMapping("/api/groups/{groupId}/prize-draws")
public class GroupPrizeDrawController {

    private final PrizeDrawService prizeDrawService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupPrizeDrawController(
        PrizeDrawService prizeDrawService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.prizeDrawService = prizeDrawService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public PrizeDrawListResponse list(@PathVariable Long groupId, HttpServletRequest request) {
        String email = requireRequestEmail(request);
        return handle(() -> prizeDrawService.list(groupId, email));
    }

    @PostMapping
    public PrizeDrawListResponse save(
        @PathVariable Long groupId,
        @RequestBody PrizeDrawSaveRequest body,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> prizeDrawService.save(groupId, email, accessControlService.resolveDisplayNickname(email), body));
    }

    @DeleteMapping("/{drawId}")
    public PrizeDrawListResponse delete(
        @PathVariable Long groupId,
        @PathVariable Long drawId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> prizeDrawService.delete(groupId, drawId, email, accessControlService.resolveDisplayNickname(email)));
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
        } catch (BoardForbiddenException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
