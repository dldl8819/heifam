package com.balancify.backend.api.access;

import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.AccessPersonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Emails grouped as one person (같은 사람 묶기), for super admins: the groups, suggestions from the
 * nicknames, and every email with access to choose from. Emails go out only to super admins and
 * are never kept by the browser (no-store).
 */
@RestController
@RequestMapping("/api/access/people")
public class AccessPeopleController {

    public record GroupRequest(List<String> emails, String displayName) {
    }

    public record RenameRequest(String displayName) {
    }

    public record UngroupRequest(String email) {
    }

    private final AccessPersonService accessPersonService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public AccessPeopleController(
        AccessPersonService accessPersonService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.accessPersonService = accessPersonService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public AccessPersonService.Overview overview(HttpServletRequest request, HttpServletResponse response) {
        requireSuperAdmin(request);
        return answer(response, accessPersonService::overview);
    }

    @PostMapping
    public AccessPersonService.Overview group(
        @RequestBody GroupRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String actor = requireSuperAdmin(request);
        return answer(response, () -> accessPersonService.group(
            body == null ? null : body.emails(),
            body == null ? null : body.displayName(),
            actor,
            actorNickname(actor)
        ));
    }

    @PatchMapping("/{personId}")
    public AccessPersonService.Overview rename(
        @PathVariable Long personId,
        @RequestBody RenameRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String actor = requireSuperAdmin(request);
        return answer(response, () -> accessPersonService.rename(
            personId,
            body == null ? null : body.displayName(),
            actor,
            actorNickname(actor)
        ));
    }

    // The email goes in the body, not the address, so it stays out of request logs.
    @PostMapping("/ungroup")
    public AccessPersonService.Overview ungroup(
        @RequestBody UngroupRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String actor = requireSuperAdmin(request);
        return answer(response, () -> accessPersonService.ungroup(
            body == null ? null : body.email(),
            actor,
            actorNickname(actor)
        ));
    }

    private AccessPersonService.Overview answer(HttpServletResponse response, Supplier<AccessPersonService.Overview> action) {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        try {
            return action.get();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    private String requireSuperAdmin(HttpServletRequest request) {
        String email = authenticatedRequestResolver.resolve(request).email();
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        if (!accessControlService.isSuperAdminEmail(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin role required");
        }
        return email;
    }

    private String actorNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
