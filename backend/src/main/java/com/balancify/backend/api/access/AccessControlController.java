package com.balancify.backend.api.access;

import com.balancify.backend.api.access.dto.AccessAdminListResponse;
import com.balancify.backend.api.access.dto.AccessAllowedEmailListResponse;
import com.balancify.backend.api.access.dto.AccessEmailEntryResponse;
import com.balancify.backend.api.access.dto.AccessEmailUpsertRequest;
import com.balancify.backend.api.access.dto.AccessMeResponse;
import com.balancify.backend.api.access.dto.AccessRaceUpdateRequest;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.AccessControlService.AccessProfile;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

@RestController
@RequestMapping("/api/access")
public class AccessControlController {

    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";

    private final AccessControlService accessControlService;

    public AccessControlController(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @GetMapping("/me")
    public AccessMeResponse getMyAccess(HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        AccessProfile profile = accessControlService.resolveAccessProfile(requestEmail);
        return new AccessMeResponse(
            profile.email(),
            profile.nickname(),
            profile.role(),
            profile.admin(),
            profile.superAdmin(),
            profile.allowed(),
            profile.preferredRace()
        );
    }

    @PutMapping("/me/race")
    public AccessMeResponse updateMyRace(
        @RequestBody AccessRaceUpdateRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        String race = safeTrim(requestBody == null ? null : requestBody.race());
        if (race.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Race is required");
        }

        try {
            AccessProfile profile = accessControlService.upsertPreferredRace(requestEmail, race);
            return new AccessMeResponse(
                profile.email(),
                profile.nickname(),
                profile.role(),
                profile.admin(),
                profile.superAdmin(),
                profile.allowed(),
                profile.preferredRace()
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    @GetMapping("/admins")
    public AccessAdminListResponse getAdminEmails(HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        requireAdmin(requestEmail);

        AccessControlService.AdminEmailSnapshot snapshot = accessControlService.getAdminEmailSnapshot();
        return new AccessAdminListResponse(
            toEntryResponses(snapshot.superAdmins()),
            toEntryResponses(snapshot.admins())
        );
    }

    @PostMapping("/admins")
    public AccessAdminListResponse addAdminEmail(
        @RequestBody AccessEmailUpsertRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        requireSuperAdmin(requestEmail);
        String targetEmail = requireBodyEmail(requestBody);
        String targetNickname = requireBodyNickname(requestBody);

        try {
            AccessControlService.AdminEmailSnapshot snapshot = accessControlService.addManagedAdminEmail(
                requestEmail,
                targetEmail,
                targetNickname
            );
            return new AccessAdminListResponse(
                toEntryResponses(snapshot.superAdmins()),
                toEntryResponses(snapshot.admins())
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    @DeleteMapping("/admins/{email}")
    public AccessAdminListResponse removeAdminEmail(
        @PathVariable String email,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        requireSuperAdmin(requestEmail);

        try {
            AccessControlService.AdminEmailSnapshot snapshot = accessControlService.removeManagedAdminEmail(
                requestEmail,
                email
            );
            return new AccessAdminListResponse(
                toEntryResponses(snapshot.superAdmins()),
                toEntryResponses(snapshot.admins())
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    @GetMapping("/allowed-users")
    public AccessAllowedEmailListResponse getAllowedEmails(HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        requireAdmin(requestEmail);

        AccessControlService.AllowedEmailSnapshot snapshot = accessControlService.getAllowedEmailSnapshot();
        return new AccessAllowedEmailListResponse(toEntryResponses(snapshot.allowedUsers()));
    }

    @PostMapping("/allowed-users")
    public AccessAllowedEmailListResponse addAllowedEmail(
        @RequestBody AccessEmailUpsertRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        requireAdmin(requestEmail);
        String targetEmail = requireBodyEmail(requestBody);
        String targetNickname = requireBodyNickname(requestBody);

        try {
            AccessControlService.AllowedEmailSnapshot snapshot = accessControlService.addAllowedUserEmail(
                requestEmail,
                targetEmail,
                targetNickname
            );
            return new AccessAllowedEmailListResponse(toEntryResponses(snapshot.allowedUsers()));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    @DeleteMapping("/allowed-users/{email}")
    public AccessAllowedEmailListResponse removeAllowedEmail(
        @PathVariable String email,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        requireAdmin(requestEmail);

        try {
            AccessControlService.AllowedEmailSnapshot snapshot = accessControlService.removeAllowedUserEmail(
                requestEmail,
                email
            );
            return new AccessAllowedEmailListResponse(toEntryResponses(snapshot.allowedUsers()));
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    private void requireAdmin(String email) {
        if (!accessControlService.isAdminEmail(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private void requireSuperAdmin(String email) {
        if (!accessControlService.isSuperAdminEmail(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin role required");
        }
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = safeTrim(request == null ? null : request.getHeader(USER_EMAIL_HEADER));
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "X-USER-EMAIL header is required"
            );
        }
        return requestEmail;
    }

    private String requireBodyEmail(AccessEmailUpsertRequest requestBody) {
        String email = safeTrim(requestBody == null ? null : requestBody.email());
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return email;
    }

    private String requireBodyNickname(AccessEmailUpsertRequest requestBody) {
        String nickname = safeTrim(requestBody == null ? null : requestBody.nickname());
        if (nickname.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname is required");
        }
        return nickname;
    }

    private List<AccessEmailEntryResponse> toEntryResponses(
        List<AccessControlService.AccessEmailEntry> entries
    ) {
        return entries.stream()
            .map(entry -> new AccessEmailEntryResponse(entry.email(), entry.nickname()))
            .toList();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
