package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.NoticeCreateRequest;
import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.api.group.dto.NoticeUpdateRequest;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.repository.NoticeEngagementRepository;
import com.balancify.backend.repository.NoticeRepository;
import com.balancify.backend.repository.NoticeVoteRepository;
import com.balancify.backend.repository.NoticeVoteRepository.OptionRow;
import com.balancify.backend.service.exception.NoticeVoteConflictException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeAdminService {

    private static final int MAX_TITLE_LENGTH = 200;

    private final NoticeRepository noticeRepository;
    private final NoticeEngagementRepository noticeEngagementRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;
    private final NotificationService notificationService;
    private final NoticeImageService noticeImageService;
    private final NoticeVoteRepository noticeVoteRepository;

    public NoticeAdminService(
        NoticeRepository noticeRepository,
        NoticeEngagementRepository noticeEngagementRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService,
        NotificationService notificationService,
        NoticeImageService noticeImageService,
        NoticeVoteRepository noticeVoteRepository
    ) {
        this.noticeRepository = noticeRepository;
        this.noticeEngagementRepository = noticeEngagementRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
        this.notificationService = notificationService;
        this.noticeImageService = noticeImageService;
        this.noticeVoteRepository = noticeVoteRepository;
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
        String voteStatus = request != null && request.voteStatus() != null
            ? NoticeVotes.requireStatus(request.voteStatus())
            : NoticeVotes.NONE;
        String content = requireContent(request == null ? null : request.content(), voteStatus);
        // Checked before anything is saved: a vote without two things to choose between is no vote.
        List<String> voteOptions = NoticeVotes.asksForVote(voteStatus)
            ? NoticeVotes.requireOptions(request.voteOptions())
            : List.of();

        Notice notice = new Notice();
        notice.setGroupId(groupId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAuthorEmail(safeTrim(actorEmail).toLowerCase(Locale.ROOT));
        notice.setAdminOnly(request != null && Boolean.TRUE.equals(request.adminOnly()));
        notice.setVoteStatus(voteStatus);
        // Anonymous unless the writer says otherwise, as every vote was before there was a choice.
        notice.setVoteAnonymous(request == null || !Boolean.FALSE.equals(request.voteAnonymous()));
        notice.setVoteAllowAdditions(request != null && Boolean.TRUE.equals(request.voteAllowAdditions()));
        noticeRepository.save(notice);
        if (!voteOptions.isEmpty()) {
            noticeVoteRepository.replaceOptions(notice.getId(), voteOptions, notice.getAuthorEmail());
        }
        // Fails the whole save when the text names an image this notice cannot show.
        noticeImageService.placeInNotice(groupId, notice.getId(), content);

        operationAuditLogService.recordNoticePosted(actorEmail, actorNickname, groupId, notice);
        notificationService.publishNotice(groupId, notice.getId(), notice.getTitle(), notice.isAdminOnly(), actorEmail);

        return new NoticeResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            actorNickname,
            notice.getCreatedAt(),
            notice.getUpdatedAt(),
            notice.isAdminOnly(),
            notice.getVoteStatus()
        );
    }

    /**
     * Saves an edit. Asked to announce it again, it starts a new revision: the notice turns unread
     * for everyone but its editor, reading it earns the reading point once more, and its readers
     * are notified of the edit. Without that, an edit changes the text and nothing else.
     */
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

        // Locked before the revision time is taken, so reads in flight end up on the right side of it.
        Notice notice = noticeRepository.findByIdAndGroupIdForUpdate(noticeId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Notice not found"));
        // Left out of the request, the vote stays as it is. The votes themselves are never touched
        // here: closing keeps the result, and a vote taken off a notice comes back as it was if it
        // is put on again.
        String voteStatus = request != null && request.voteStatus() != null
            ? NoticeVotes.requireStatus(request.voteStatus())
            : notice.getVoteStatus();
        String content = requireContent(request == null ? null : request.content(), voteStatus);
        boolean wasAdminOnly = notice.isAdminOnly();
        notice.setTitle(title);
        notice.setContent(content);
        // Leaving the flag out of an update keeps it as it was.
        if (request != null && request.adminOnly() != null) {
            notice.setAdminOnly(request.adminOnly());
        }
        notice.setVoteStatus(voteStatus);
        applyVoteSettings(notice, request, safeTrim(actorEmail).toLowerCase(Locale.ROOT));
        // A notice opened up to members reaches them as a new one; they have no reads to turn back.
        boolean openedToMembers = wasAdminOnly && !notice.isAdminOnly();
        boolean closedToMembers = !wasAdminOnly && notice.isAdminOnly();
        boolean announcedAgain = request != null && Boolean.TRUE.equals(request.announceAgain()) && !openedToMembers;
        if (announcedAgain) {
            notice.setRevision(notice.getRevision() + 1);
            notice.setRevisedAt(NoticeRevisions.now());
        }
        noticeRepository.save(notice);
        noticeImageService.placeInNotice(groupId, notice.getId(), content);

        operationAuditLogService.recordNoticeUpdated(actorEmail, actorNickname, groupId, notice, announcedAgain);
        if (openedToMembers) {
            notificationService.publishNotice(groupId, notice.getId(), notice.getTitle(), false, actorEmail);
        } else if (announcedAgain) {
            // The editor has read what they wrote; saved here, their read earns no point for it.
            noticeEngagementRepository.markRead(
                notice.getId(),
                safeTrim(actorEmail).toLowerCase(Locale.ROOT),
                notice.getRevisedAt(),
                notice.getRevisedAt()
            );
            notificationService.publishNoticeRevised(
                groupId, notice.getId(), notice.getTitle(), notice.isAdminOnly(), actorEmail
            );
        } else if (closedToMembers) {
            // Members can no longer open it, so its notification stops showing them its title.
            notificationService.removeNotice(notice.getId());
        }

        String authorNickname = safeTrim(
            accessControlService.resolveAccessProfile(notice.getAuthorEmail()).nickname()
        );
        return new NoticeResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            authorNickname.isEmpty() ? null : authorNickname,
            notice.getCreatedAt(),
            notice.getUpdatedAt(),
            notice.isAdminOnly(),
            notice.getVoteStatus()
        );
    }

    @Transactional
    public void deleteNotice(Long groupId, Long noticeId, String actorEmail, String actorNickname) {
        // Admins can post and edit notices, but removing one is reserved for super admins.
        requireSuperAdmin(actorEmail);

        Notice notice = noticeRepository.findByIdAndGroupId(noticeId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Notice not found"));
        noticeRepository.delete(notice);
        notificationService.removeNotice(notice.getId());

        operationAuditLogService.recordNoticeDeleted(actorEmail, actorNickname, groupId, notice.getId(), notice.getTitle());
    }

    /**
     * What an edit may change about the vote besides opening and closing it. The options can be
     * rewritten only while nobody has voted, since a vote is for an option as it read when it was
     * cast. An anonymous vote that has votes stays anonymous: those who voted were told nobody
     * would see what they chose. Whether voters may add options can change at any time.
     */
    private void applyVoteSettings(Notice notice, NoticeUpdateRequest request, String actor) {
        List<OptionRow> current = noticeVoteRepository.listOptions(notice.getId());
        long votes = current.stream().mapToLong(OptionRow::voteCount).sum();

        if (NoticeVotes.asksForVote(notice.getVoteStatus())) {
            if (request != null && request.voteOptions() != null) {
                List<String> wanted = NoticeVotes.requireOptions(request.voteOptions());
                if (!wanted.equals(current.stream().map(OptionRow::label).toList())) {
                    if (votes > 0) {
                        throw new NoticeVoteConflictException("이미 투표한 사람이 있어 항목을 바꿀 수 없습니다.");
                    }
                    noticeVoteRepository.replaceOptions(notice.getId(), wanted, actor);
                }
            } else if (current.isEmpty()) {
                // A vote put on a notice that never had one, without naming options: for or against.
                noticeVoteRepository.replaceOptions(notice.getId(), NoticeVotes.DEFAULT_OPTIONS, actor);
            }
        }
        if (request != null && request.voteAnonymous() != null && request.voteAnonymous() != notice.isVoteAnonymous()) {
            if (!request.voteAnonymous() && votes > 0) {
                throw new NoticeVoteConflictException("익명으로 받은 투표는 기명 투표로 바꿀 수 없습니다.");
            }
            notice.setVoteAnonymous(request.voteAnonymous());
        }
        if (request != null && request.voteAllowAdditions() != null) {
            notice.setVoteAllowAdditions(request.voteAllowAdditions());
        }
    }

    private void requireAdmin(String actorEmail) {
        if (!accessControlService.isAdminEmail(actorEmail)) {
            throw new IllegalArgumentException("Only admins can manage notices");
        }
    }

    private void requireSuperAdmin(String actorEmail) {
        if (!accessControlService.isSuperAdminEmail(actorEmail)) {
            throw new IllegalArgumentException("Only super admins can delete notices");
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

    /**
     * A notice needs its text, unless it asks for a vote: then the title is the question and the
     * vote is what the notice shows, with text only if the writer adds some.
     */
    private String requireContent(String value, String voteStatus) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty() && NoticeVotes.NONE.equals(voteStatus)) {
            throw new IllegalArgumentException("Content is required");
        }
        return trimmed;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
