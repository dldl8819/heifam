package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.NoticeCreateRequest;
import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.api.group.dto.NoticeUpdateRequest;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.repository.NoticeRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeAdminService {

    private static final int MAX_TITLE_LENGTH = 200;

    private final NoticeRepository noticeRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;

    public NoticeAdminService(
        NoticeRepository noticeRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.noticeRepository = noticeRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional
    public NoticeResponse createNotice(
        Long groupId,
        NoticeCreateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);
        String title = requireTitle(request == null ? null : request.title());
        String content = requireContent(request == null ? null : request.content());

        Notice notice = new Notice();
        notice.setGroupId(groupId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAuthorEmail(safeTrim(actorEmail).toLowerCase(java.util.Locale.ROOT));
        noticeRepository.save(notice);

        operationAuditLogService.recordNoticePosted(actorEmail, actorNickname, groupId, notice);

        return new NoticeResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            actorNickname,
            notice.getCreatedAt(),
            notice.getUpdatedAt()
        );
    }

    @Transactional
    public NoticeResponse updateNotice(
        Long groupId,
        Long noticeId,
        NoticeUpdateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        requireAdmin(actorEmail);
        String title = requireTitle(request == null ? null : request.title());
        String content = requireContent(request == null ? null : request.content());

        Notice notice = noticeRepository.findByIdAndGroupId(noticeId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Notice not found"));
        notice.setTitle(title);
        notice.setContent(content);
        noticeRepository.save(notice);

        operationAuditLogService.recordNoticeUpdated(actorEmail, actorNickname, groupId, notice);

        String authorNickname = safeTrim(
            accessControlService.resolveAccessProfile(notice.getAuthorEmail()).nickname()
        );
        return new NoticeResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            authorNickname.isEmpty() ? null : authorNickname,
            notice.getCreatedAt(),
            notice.getUpdatedAt()
        );
    }

    @Transactional
    public void deleteNotice(Long groupId, Long noticeId, String actorEmail, String actorNickname) {
        requireAdmin(actorEmail);

        Notice notice = noticeRepository.findByIdAndGroupId(noticeId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Notice not found"));
        noticeRepository.delete(notice);

        operationAuditLogService.recordNoticeDeleted(actorEmail, actorNickname, groupId, notice.getId(), notice.getTitle());
    }

    private void requireAdmin(String actorEmail) {
        if (!accessControlService.isAdminEmail(actorEmail)) {
            throw new IllegalArgumentException("Only admins can manage notices");
        }
    }

    private String requireTitle(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title must be " + MAX_TITLE_LENGTH + " characters or fewer");
        }
        return trimmed;
    }

    private String requireContent(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Content is required");
        }
        return trimmed;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
