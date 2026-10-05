package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.NoticeCommentResponse;
import com.balancify.backend.api.group.dto.NoticeDetailResponse;
import com.balancify.backend.api.group.dto.NoticeListItemResponse;
import com.balancify.backend.api.group.dto.NoticeListResponse;
import com.balancify.backend.api.group.dto.NoticeTitleResponse;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.domain.NoticeComment;
import com.balancify.backend.repository.NoticeCommentRepository;
import com.balancify.backend.repository.NoticeEngagementRepository;
import com.balancify.backend.repository.NoticeRepository;
import com.balancify.backend.service.exception.NoticeForbiddenException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notices as members see them. Visitors get the titles only; members open notices, which marks
 * them read, and like and comment on them. A notice kept to admins is invisible to everyone else.
 * An edit announced again (NoticeAdminService) turns the notice unread for everyone.
 */
@Service
public class NoticeService {

    static final int MAX_COMMENT_LENGTH = 500;

    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final NoticeEngagementRepository noticeEngagementRepository;
    private final AccessControlService accessControlService;
    private final PointService pointService;

    public NoticeService(
        NoticeRepository noticeRepository,
        NoticeCommentRepository noticeCommentRepository,
        NoticeEngagementRepository noticeEngagementRepository,
        AccessControlService accessControlService,
        PointService pointService
    ) {
        this.noticeRepository = noticeRepository;
        this.noticeCommentRepository = noticeCommentRepository;
        this.noticeEngagementRepository = noticeEngagementRepository;
        this.accessControlService = accessControlService;
        this.pointService = pointService;
    }

    @Transactional(readOnly = true)
    public List<NoticeTitleResponse> listTitles(Long groupId) {
        return noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId).stream()
            .filter(notice -> !notice.isAdminOnly())
            .map(notice -> new NoticeTitleResponse(notice.getTitle(), notice.getCreatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public NoticeListResponse list(Long groupId, String email) {
        String reader = normalizeEmail(email);
        boolean admin = accessControlService.isAdminEmail(reader);
        List<Notice> notices = noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId).stream()
            .filter(notice -> admin || !notice.isAdminOnly())
            .toList();
        List<Long> noticeIds = notices.stream().map(Notice::getId).toList();
        Set<Long> readIds = noticeEngagementRepository.findReadNoticeIds(reader, noticeIds);
        Map<Long, Long> likeCounts = noticeEngagementRepository.countLikes(noticeIds);
        Map<Long, Long> commentCounts = new HashMap<>();
        if (!noticeIds.isEmpty()) {
            noticeCommentRepository.countByNotice(noticeIds)
                .forEach(count -> commentCounts.put(count.getNoticeId(), count.getTotal()));
        }
        Map<String, String> nicknames = nicknames(notices.stream().map(Notice::getAuthorEmail).toList());

        List<NoticeListItemResponse> items = notices.stream()
            .map(notice -> new NoticeListItemResponse(
                notice.getId(),
                notice.getTitle(),
                nicknames.get(normalizeEmail(notice.getAuthorEmail())),
                notice.getCreatedAt(),
                notice.isAdminOnly(),
                readIds.contains(notice.getId()),
                notice.getRevision() > 0,
                likeCounts.getOrDefault(notice.getId(), 0L),
                commentCounts.getOrDefault(notice.getId(), 0L)
            ))
            .toList();
        int readCount = (int) items.stream().filter(NoticeListItemResponse::read).count();
        return new NoticeListResponse(items, readCount, items.size() - readCount);
    }

    /**
     * Opening a notice marks its current revision read for this member and earns the reading point
     * once per revision. The notice as first posted pays whoever opens it, as it always did. A
     * re-announced edit pays those this call turns it read for, which leaves out its editor, whose
     * read was saved with the edit.
     */
    @Transactional
    public NoticeDetailResponse open(Long groupId, Long noticeId, String email) {
        String reader = normalizeEmail(email);
        Notice notice = requireVisible(noticeRepository.findByIdAndGroupIdForShare(noticeId, groupId), reader);
        boolean newlyRead = noticeEngagementRepository.markRead(
            notice.getId(),
            reader,
            NoticeRevisions.readTime(notice.getRevisedAt()),
            notice.getRevisedAt()
        );
        if (notice.getRevision() == 0 || newlyRead) {
            pointService.grantNoticeReadPoint(reader, notice.getId(), notice.getRevision());
        }
        return detail(notice, reader);
    }

    @Transactional
    public NoticeDetailResponse addComment(Long groupId, Long noticeId, String email, String content) {
        String author = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, author);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 1~" + MAX_COMMENT_LENGTH + "자로 입력해 주세요.");
        }
        NoticeComment comment = new NoticeComment();
        comment.setNoticeId(notice.getId());
        comment.setAuthorEmail(author);
        comment.setContent(text);
        noticeCommentRepository.save(comment);
        pointService.grantNoticePoint(author, notice.getId(), PointService.REASON_NOTICE_COMMENT);
        return detail(notice, author);
    }

    /** Writers remove their own comments; admins can remove any. */
    @Transactional
    public NoticeDetailResponse deleteComment(Long groupId, Long noticeId, Long commentId, String email) {
        String actor = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, actor);
        NoticeComment comment = noticeCommentRepository.findByIdAndNoticeId(commentId, notice.getId())
            .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (!actor.equals(normalizeEmail(comment.getAuthorEmail())) && !accessControlService.isAdminEmail(actor)) {
            throw new NoticeForbiddenException("본인 댓글만 지울 수 있습니다.");
        }
        noticeCommentRepository.delete(comment);
        return detail(notice, actor);
    }

    @Transactional
    public NoticeDetailResponse setLike(Long groupId, Long noticeId, String email, boolean liked) {
        String member = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, member);
        if (liked) {
            noticeEngagementRepository.like(notice.getId(), member);
            pointService.grantNoticePoint(member, notice.getId(), PointService.REASON_NOTICE_LIKE);
        } else {
            noticeEngagementRepository.unlike(notice.getId(), member);
        }
        return detail(notice, member);
    }

    private NoticeDetailResponse detail(Notice notice, String reader) {
        boolean admin = accessControlService.isAdminEmail(reader);
        List<NoticeComment> comments = noticeCommentRepository.findByNoticeIdOrderByIdAsc(notice.getId());
        List<String> emails = new ArrayList<>();
        emails.add(notice.getAuthorEmail());
        comments.forEach(comment -> emails.add(comment.getAuthorEmail()));
        Map<String, String> nicknames = nicknames(emails);
        return new NoticeDetailResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            nicknames.get(normalizeEmail(notice.getAuthorEmail())),
            notice.getCreatedAt(),
            notice.getUpdatedAt(),
            notice.isAdminOnly(),
            noticeEngagementRepository.countLikes(List.of(notice.getId())).getOrDefault(notice.getId(), 0L),
            noticeEngagementRepository.hasLiked(notice.getId(), reader),
            comments.stream()
                .map(comment -> {
                    boolean mine = reader.equals(normalizeEmail(comment.getAuthorEmail()));
                    return new NoticeCommentResponse(
                        comment.getId(),
                        nicknames.get(normalizeEmail(comment.getAuthorEmail())),
                        comment.getContent(),
                        comment.getCreatedAt(),
                        mine,
                        mine || admin
                    );
                })
                .toList()
        );
    }

    private Notice requireVisible(Long groupId, Long noticeId, String email) {
        return requireVisible(noticeRepository.findByIdAndGroupId(noticeId, groupId), email);
    }

    private Notice requireVisible(Optional<Notice> found, String email) {
        Notice notice = found.orElseThrow(() -> new NoSuchElementException("Notice not found"));
        if (notice.isAdminOnly() && !accessControlService.isAdminEmail(email)) {
            throw new NoSuchElementException("Notice not found");
        }
        return notice;
    }

    private Map<String, String> nicknames(List<String> emails) {
        Set<String> unique = new LinkedHashSet<>();
        emails.forEach(email -> {
            String normalized = normalizeEmail(email);
            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
        });
        Map<String, String> nicknames = new LinkedHashMap<>();
        if (!unique.isEmpty()) {
            accessControlService.resolveDisplayNicknames(unique).forEach((email, nickname) -> {
                if (nickname != null) {
                    nicknames.put(email, nickname);
                }
            });
        }
        return nicknames;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
