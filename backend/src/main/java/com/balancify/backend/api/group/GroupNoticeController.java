package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.NoticeCommentRequest;
import com.balancify.backend.api.group.dto.NoticeDetailResponse;
import com.balancify.backend.api.group.dto.NoticeListResponse;
import com.balancify.backend.api.group.dto.NoticeTitleResponse;
import com.balancify.backend.api.group.dto.NoticeVoteOptionRequest;
import com.balancify.backend.api.group.dto.NoticeVoteRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.NoticeService;
import com.balancify.backend.service.exception.NoticeForbiddenException;
import com.balancify.backend.service.exception.NoticeVoteClosedException;
import com.balancify.backend.service.exception.NoticeVoteConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
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
 * Notices for members. The title list is the one part open to anyone (ServiceAccessFilter lets
 * GET notice-titles through); everything else needs member access.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupNoticeController {

    private final NoticeService noticeService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupNoticeController(NoticeService noticeService, AuthenticatedRequestResolver authenticatedRequestResolver) {
        this.noticeService = noticeService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/{groupId}/notice-titles")
    public List<NoticeTitleResponse> getNoticeTitles(@PathVariable Long groupId) {
        return noticeService.listTitles(groupId);
    }

    @GetMapping("/{groupId}/notices")
    public NoticeListResponse getNotices(@PathVariable Long groupId, HttpServletRequest request) {
        return noticeService.list(groupId, requireRequestEmail(request));
    }

    @GetMapping("/{groupId}/notices/{noticeId}")
    public NoticeDetailResponse getNotice(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.open(groupId, noticeId, requestEmail));
    }

    @PostMapping("/{groupId}/notices/{noticeId}/comments")
    public NoticeDetailResponse addComment(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @RequestBody NoticeCommentRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.addComment(
            groupId,
            noticeId,
            requestEmail,
            requestBody == null ? null : requestBody.content(),
            requestBody == null ? null : requestBody.parentId()
        ));
    }

    @PutMapping("/{groupId}/notices/{noticeId}/comments/{commentId}")
    public NoticeDetailResponse editComment(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @PathVariable Long commentId,
        @RequestBody NoticeCommentRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.editComment(
            groupId,
            noticeId,
            commentId,
            requestEmail,
            requestBody == null ? null : requestBody.content()
        ));
    }

    @DeleteMapping("/{groupId}/notices/{noticeId}/comments/{commentId}")
    public NoticeDetailResponse deleteComment(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @PathVariable Long commentId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.deleteComment(groupId, noticeId, commentId, requestEmail));
    }

    @PutMapping("/{groupId}/notices/{noticeId}/comments/{commentId}/like")
    public NoticeDetailResponse likeComment(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @PathVariable Long commentId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.setCommentLike(groupId, noticeId, commentId, requestEmail, true));
    }

    @DeleteMapping("/{groupId}/notices/{noticeId}/comments/{commentId}/like")
    public NoticeDetailResponse unlikeComment(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @PathVariable Long commentId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.setCommentLike(groupId, noticeId, commentId, requestEmail, false));
    }

    @PutMapping("/{groupId}/notices/{noticeId}/vote")
    public NoticeDetailResponse vote(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @RequestBody NoticeVoteRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.castVote(
            groupId,
            noticeId,
            requestEmail,
            requestBody == null ? null : requestBody.optionId(),
            requestBody == null ? null : requestBody.choice()
        ));
    }

    @PostMapping("/{groupId}/notices/{noticeId}/vote/options")
    public NoticeDetailResponse addVoteOption(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @RequestBody NoticeVoteOptionRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.addVoteOption(
            groupId,
            noticeId,
            requestEmail,
            requestBody == null ? null : requestBody.label()
        ));
    }

    /** Admins only (AdminKeyFilter, and NoticeService checks again): the votes on the option go with it. */
    @DeleteMapping("/{groupId}/notices/{noticeId}/vote/options/{optionId}")
    public NoticeDetailResponse removeVoteOption(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        @PathVariable Long optionId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.removeVoteOption(groupId, noticeId, optionId, requestEmail));
    }

    @DeleteMapping("/{groupId}/notices/{noticeId}/vote")
    public NoticeDetailResponse withdrawVote(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.withdrawVote(groupId, noticeId, requestEmail));
    }

    @PutMapping("/{groupId}/notices/{noticeId}/like")
    public NoticeDetailResponse like(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.setLike(groupId, noticeId, requestEmail, true));
    }

    @DeleteMapping("/{groupId}/notices/{noticeId}/like")
    public NoticeDetailResponse unlike(
        @PathVariable Long groupId,
        @PathVariable Long noticeId,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        return handle(() -> noticeService.setLike(groupId, noticeId, requestEmail, false));
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
        } catch (NoticeForbiddenException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (NoticeVoteClosedException | NoticeVoteConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
