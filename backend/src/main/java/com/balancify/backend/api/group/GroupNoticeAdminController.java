package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.NoticeCreateRequest;
import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.api.group.dto.NoticeUpdateRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.NoticeAdminService;
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
public class GroupNoticeAdminController {

    private final NoticeAdminService noticeAdminService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupNoticeAdminController(
        NoticeAdminService noticeAdminService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.noticeAdminService = noticeAdminService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @PostMapping("/{groupId}/notices")
    public NoticeResponse createNotice(
        @PathVariable Long groupId,
        @RequestBody NoticeCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);

        try {
            return noticeAdminService.createNotice(
                groupId,
                request,
                requestEmail,
                resolveActorNickname(requestEmail)
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    @PutMapping("/{groupId}/notices/{noticeId}")
    public NoticeResponse updateNotice(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @RequestBody NoticeUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);

        try {
            return noticeAdminService.updateNotice(
                groupId,
                noticeId,
                request,
                requestEmail,
                resolveActorNickname(requestEmail)
            );
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

    @DeleteMapping("/{groupId}/notices/{noticeId}")
    public void deleteNotice(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        requireAdmin(requestEmail);

        try {
            noticeAdminService.deleteNotice(groupId, noticeId, requestEmail, resolveActorNickname(requestEmail));
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    private void requireAdmin(String email) {
        if (!accessControlService.isAdminEmail(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Valid Supabase bearer token is required"
            );
        }
        return requestEmail;
    }

    private String resolveActorNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
