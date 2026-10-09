package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.NoticeCommentResponse;
import com.balancify.backend.api.group.dto.NoticeDetailResponse;
import com.balancify.backend.api.group.dto.NoticeListItemResponse;
import com.balancify.backend.api.group.dto.NoticeListResponse;
import com.balancify.backend.api.group.dto.NoticeTitleResponse;
import com.balancify.backend.api.group.dto.NoticeVoteKeptResponse;
import com.balancify.backend.api.group.dto.NoticeVoteOptionResponse;
import com.balancify.backend.api.group.dto.NoticeVoteResponse;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.domain.NoticeComment;
import com.balancify.backend.repository.NoticeCommentRepository;
import com.balancify.backend.repository.NoticeEngagementRepository;
import com.balancify.backend.repository.NoticeRepository;
import com.balancify.backend.repository.NoticeVoteRepository;
import com.balancify.backend.repository.NoticeVoteRepository.OptionRow;
import com.balancify.backend.repository.NoticeVoteRepository.VoterRow;
import com.balancify.backend.service.exception.NoticeForbiddenException;
import com.balancify.backend.service.exception.NoticeVoteClosedException;
import com.balancify.backend.service.exception.NoticeVoteConflictException;
import java.time.OffsetDateTime;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notices as members see them. Visitors get the titles only; members open notices, which marks
 * them read, and like and comment on them. A notice kept to admins is invisible to everyone else.
 * An edit announced again (NoticeAdminService) turns the notice unread for everyone.
 *
 * <p>Comments can be answered, one level deep, edited by their writer and liked by others. A
 * comment deleted while it has replies stays as an emptied place until its last reply is gone.
 *
 * <p>A notice can ask for a vote on a list of options: 찬성 and 반대, or whatever its writer named.
 * Everyone who may open the notice sees how many chose each. An anonymous vote shows who chose
 * what to nobody; a named one lists the voters under each option. When the vote allows it, voters
 * add options of their own.
 */
@Service
public class NoticeService {

    static final int MAX_COMMENT_LENGTH = 500;

    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final NoticeEngagementRepository noticeEngagementRepository;
    private final AccessControlService accessControlService;
    private final PointService pointService;
    private final NoticeVoteRepository noticeVoteRepository;
    private final OperationAuditLogService operationAuditLogService;

    public NoticeService(
        NoticeRepository noticeRepository,
        NoticeCommentRepository noticeCommentRepository,
        NoticeEngagementRepository noticeEngagementRepository,
        AccessControlService accessControlService,
        PointService pointService,
        NoticeVoteRepository noticeVoteRepository,
        OperationAuditLogService operationAuditLogService
    ) {
        this.noticeRepository = noticeRepository;
        this.noticeCommentRepository = noticeCommentRepository;
        this.noticeEngagementRepository = noticeEngagementRepository;
        this.accessControlService = accessControlService;
        this.pointService = pointService;
        this.noticeVoteRepository = noticeVoteRepository;
        this.operationAuditLogService = operationAuditLogService;
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
        Map<Long, Long> viewCounts = noticeEngagementRepository.countReads(noticeIds);
        Map<Long, Long> voterCounts = noticeEngagementRepository.countVoters(noticeIds);
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
                commentCounts.getOrDefault(notice.getId(), 0L),
                NoticeVotes.OPEN.equals(notice.getVoteStatus()),
                viewCounts.getOrDefault(notice.getId(), 0L),
                // A vote that was taken off the notice keeps its votes but shows none of them.
                asksForVote(notice) ? voterCounts.getOrDefault(notice.getId(), 0L) : null
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

    /**
     * A comment on the notice, or with parentId a reply. A reply to a reply is filed under the
     * comment that one answers, so a thread never goes deeper than one level. Replies earn the
     * same once-per-notice commenting point as comments.
     */
    @Transactional
    public NoticeDetailResponse addComment(Long groupId, Long noticeId, String email, String content, Long parentId) {
        String author = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, author);
        String text = requireCommentText(content);
        NoticeComment comment = new NoticeComment();
        comment.setNoticeId(notice.getId());
        comment.setAuthorEmail(author);
        comment.setContent(text);
        if (parentId != null) {
            NoticeComment answered = noticeCommentRepository.findByIdAndNoticeId(parentId, notice.getId())
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
            comment.setParentId(answered.getParentId() != null ? answered.getParentId() : answered.getId());
        }
        noticeCommentRepository.save(comment);
        pointService.grantNoticePoint(author, notice.getId(), PointService.REASON_NOTICE_COMMENT);
        return detail(notice, author);
    }

    /** Only the writer changes a comment's text; admins can remove a comment but not reword it. */
    @Transactional
    public NoticeDetailResponse editComment(Long groupId, Long noticeId, Long commentId, String email, String content) {
        String actor = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, actor);
        NoticeComment comment = requireComment(notice, commentId);
        if (!actor.equals(normalizeEmail(comment.getAuthorEmail()))) {
            throw new NoticeForbiddenException("본인 댓글만 수정할 수 있습니다.");
        }
        String text = requireCommentText(content);
        if (!text.equals(comment.getContent())) {
            comment.setContent(text);
            comment.setEditedAt(OffsetDateTime.now());
            noticeCommentRepository.save(comment);
        }
        return detail(notice, actor);
    }

    /**
     * Writers remove their own comments; admins can remove any. A comment that has replies is
     * emptied instead of removed, so the replies stay where they are; it goes for good with its
     * last reply.
     */
    @Transactional
    public NoticeDetailResponse deleteComment(Long groupId, Long noticeId, Long commentId, String email) {
        String actor = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, actor);
        NoticeComment comment = requireComment(notice, commentId);
        if (!actor.equals(normalizeEmail(comment.getAuthorEmail())) && !accessControlService.isAdminEmail(actor)) {
            throw new NoticeForbiddenException("본인 댓글만 지울 수 있습니다.");
        }
        if (noticeCommentRepository.existsByParentId(comment.getId())) {
            comment.setContent("");
            comment.setAuthorEmail("");
            comment.setEditedAt(null);
            comment.setDeletedAt(OffsetDateTime.now());
            noticeCommentRepository.save(comment);
            noticeEngagementRepository.clearCommentLikes(comment.getId());
            return detail(notice, actor);
        }
        Long answeredId = comment.getParentId();
        noticeCommentRepository.delete(comment);
        if (answeredId != null) {
            // Flushed first, so the reply just removed is not counted as still being there.
            noticeCommentRepository.flush();
            noticeCommentRepository.findByIdAndNoticeId(answeredId, notice.getId())
                .filter(NoticeComment::isDeleted)
                .filter(answered -> !noticeCommentRepository.existsByParentId(answered.getId()))
                .ifPresent(noticeCommentRepository::delete);
        }
        return detail(notice, actor);
    }

    /**
     * A like on someone else's comment. The first like of a comment earns the liker a point, up
     * to PointService's daily cap; taking the like back keeps the point.
     */
    @Transactional
    public NoticeDetailResponse setCommentLike(Long groupId, Long noticeId, Long commentId, String email, boolean liked) {
        String member = normalizeEmail(email);
        Notice notice = requireVisible(groupId, noticeId, member);
        NoticeComment comment = requireComment(notice, commentId);
        if (liked) {
            if (member.equals(normalizeEmail(comment.getAuthorEmail()))) {
                throw new NoticeForbiddenException("내 댓글에는 좋아요를 누를 수 없습니다.");
            }
            noticeEngagementRepository.likeComment(comment.getId(), member);
            pointService.grantNoticeCommentLikePoint(member, comment.getId());
        } else {
            noticeEngagementRepository.unlikeComment(comment.getId(), member);
        }
        return detail(notice, member);
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

    /**
     * A member's vote on a notice that asks for one. Voting again moves the vote to another option;
     * once the vote is closed nothing changes any more. The notice is locked as for opening it,
     * so a vote and the edit that closes voting never cross. A page loaded before votes had
     * options sends AGREE or DISAGREE instead of an option, meaning 찬성 or 반대.
     */
    @Transactional
    public NoticeDetailResponse castVote(Long groupId, Long noticeId, String email, Long optionId, String legacyChoice) {
        String voter = normalizeEmail(email);
        Notice notice = requireOpenVote(noticeRepository.findByIdAndGroupIdForShare(noticeId, groupId), voter);
        List<OptionRow> options = noticeVoteRepository.listOptions(notice.getId());
        OptionRow chosen;
        if (optionId != null) {
            // Taken away since the page was drawn: the page reads the vote again.
            chosen = options.stream().filter(option -> option.id().equals(optionId)).findFirst()
                .orElseThrow(() -> new NoticeVoteConflictException("없어진 투표 항목입니다."));
        } else {
            String label = NoticeVotes.legacyLabel(legacyChoice);
            chosen = options.stream().filter(option -> option.label().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("투표할 항목을 골라 주세요."));
        }
        noticeVoteRepository.castVote(notice.getId(), voter, chosen.id());
        return detail(notice, voter);
    }

    @Transactional
    public NoticeDetailResponse withdrawVote(Long groupId, Long noticeId, String email) {
        String voter = normalizeEmail(email);
        Notice notice = requireOpenVote(noticeRepository.findByIdAndGroupIdForShare(noticeId, groupId), voter);
        noticeVoteRepository.withdrawVote(notice.getId(), voter);
        return detail(notice, voter);
    }

    /**
     * Adds an option to an open vote: an admin always may, anyone else who may open the notice
     * only when the vote allows additions. The notice is locked as for an edit, so two additions
     * at once come one after the other and a vote never holds more options than it may, nor the
     * same one twice.
     */
    @Transactional
    public NoticeDetailResponse addVoteOption(Long groupId, Long noticeId, String email, String label) {
        String actor = normalizeEmail(email);
        Notice notice = requireOpenVote(noticeRepository.findByIdAndGroupIdForUpdate(noticeId, groupId), actor);
        if (!notice.isVoteAllowAdditions() && !accessControlService.isAdminEmail(actor)) {
            throw new NoticeForbiddenException("항목을 추가할 수 없는 투표입니다.");
        }
        String cleanLabel = NoticeVotes.requireOptionLabel(label);
        List<OptionRow> options = noticeVoteRepository.listOptions(notice.getId());
        if (options.size() >= NoticeVotes.MAX_OPTIONS) {
            throw new NoticeVoteConflictException("투표 항목은 " + NoticeVotes.MAX_OPTIONS + "개까지 둘 수 있습니다.");
        }
        if (options.stream().anyMatch(option -> option.label().equalsIgnoreCase(cleanLabel))) {
            throw new NoticeVoteConflictException("이미 있는 항목입니다.");
        }
        try {
            noticeVoteRepository.addOption(notice.getId(), cleanLabel, actor);
        } catch (DuplicateKeyException exception) {
            // The same option as the database compares them, which the check above did not catch.
            throw new NoticeVoteConflictException("이미 있는 항목입니다.");
        }
        return detail(notice, actor);
    }

    /**
     * Takes an option off an open vote, with the votes on it: for admins, to clear away an option
     * that should not be there. A vote keeps at least two options. Those who had chosen it are
     * left without a vote and can vote again. The log says how many votes went, never whose.
     */
    @Transactional
    public NoticeDetailResponse removeVoteOption(Long groupId, Long noticeId, Long optionId, String email) {
        String actor = normalizeEmail(email);
        if (!accessControlService.isAdminEmail(actor)) {
            throw new NoticeForbiddenException("운영진만 투표 항목을 삭제할 수 있습니다.");
        }
        Notice notice = requireOpenVote(noticeRepository.findByIdAndGroupIdForUpdate(noticeId, groupId), actor);
        List<OptionRow> options = noticeVoteRepository.listOptions(notice.getId());
        OptionRow option = options.stream().filter(candidate -> candidate.id().equals(optionId)).findFirst()
            .orElseThrow(() -> new NoticeVoteConflictException("없어진 투표 항목입니다."));
        if (options.size() <= NoticeVotes.MIN_OPTIONS) {
            throw new IllegalArgumentException("투표 항목은 " + NoticeVotes.MIN_OPTIONS + "개 이상이어야 합니다.");
        }
        noticeVoteRepository.removeOption(notice.getId(), option.id());
        operationAuditLogService.recordNoticeVoteOptionRemoved(
            actor, accessControlService.resolveDisplayNickname(actor), groupId, notice, option.label(), option.voteCount()
        );
        return detail(notice, actor);
    }

    private Notice requireOpenVote(Optional<Notice> found, String voter) {
        Notice notice = requireVisible(found, voter);
        if (NoticeVotes.CLOSED.equals(notice.getVoteStatus())) {
            throw new NoticeVoteClosedException("투표가 마감되었습니다.");
        }
        if (!NoticeVotes.OPEN.equals(notice.getVoteStatus())) {
            throw new NoSuchElementException("Vote not found");
        }
        return notice;
    }

    private static boolean asksForVote(Notice notice) {
        return NoticeVotes.asksForVote(notice.getVoteStatus());
    }

    private NoticeVoteResponse vote(Notice notice, String reader, boolean admin) {
        String status = notice.getVoteStatus();
        if (!asksForVote(notice)) {
            return null;
        }
        List<OptionRow> options = noticeVoteRepository.listOptions(notice.getId());
        Long myOptionId = noticeVoteRepository.findVotedOptionId(notice.getId(), reader).orElse(null);
        // Looked up only for a named vote: an anonymous one never loads who voted for what.
        Map<Long, List<String>> voters = notice.isVoteAnonymous() ? null : votersByOption(notice.getId());
        boolean open = NoticeVotes.OPEN.equals(status);

        long agreeCount = 0;
        long disagreeCount = 0;
        String myChoice = null;
        for (OptionRow option : options) {
            boolean mine = option.id().equals(myOptionId);
            if (NoticeVotes.AGREE_LABEL.equals(option.label())) {
                agreeCount = option.voteCount();
                myChoice = mine ? NoticeVotes.AGREE : myChoice;
            } else if (NoticeVotes.DISAGREE_LABEL.equals(option.label())) {
                disagreeCount = option.voteCount();
                myChoice = mine ? NoticeVotes.DISAGREE : myChoice;
            }
        }
        return new NoticeVoteResponse(
            status,
            agreeCount,
            disagreeCount,
            myChoice,
            notice.isVoteAnonymous(),
            notice.isVoteAllowAdditions(),
            open && options.size() < NoticeVotes.MAX_OPTIONS && (admin || notice.isVoteAllowAdditions()),
            open && admin && options.size() > NoticeVotes.MIN_OPTIONS,
            options.stream().mapToLong(OptionRow::voteCount).sum(),
            myOptionId,
            options.stream()
                .map(option -> new NoticeVoteOptionResponse(
                    option.id(),
                    option.label(),
                    option.voteCount(),
                    option.id().equals(myOptionId),
                    voters == null ? null : voters.getOrDefault(option.id(), List.of())
                ))
                .toList()
        );
    }

    /** Nicknames of the voters under the option each chose; a voter without a nickname is a null entry. */
    private Map<Long, List<String>> votersByOption(Long noticeId) {
        List<VoterRow> rows = noticeVoteRepository.listVoters(noticeId);
        Map<String, String> nicknames = nicknames(rows.stream().map(VoterRow::voterEmail).toList());
        Map<Long, List<String>> byOption = new HashMap<>();
        rows.forEach(row -> byOption
            .computeIfAbsent(row.optionId(), id -> new ArrayList<>())
            .add(nicknames.get(normalizeEmail(row.voterEmail()))));
        return byOption;
    }

    /** A vote taken off the notice but kept, for the admin's edit form; nothing for anyone else. */
    private NoticeVoteKeptResponse voteKept(Notice notice, boolean admin) {
        if (!admin || asksForVote(notice)) {
            return null;
        }
        List<OptionRow> options = noticeVoteRepository.listOptions(notice.getId());
        if (options.isEmpty()) {
            return null;
        }
        return new NoticeVoteKeptResponse(
            options.stream().map(OptionRow::label).toList(),
            options.stream().mapToLong(OptionRow::voteCount).sum(),
            notice.isVoteAnonymous(),
            notice.isVoteAllowAdditions()
        );
    }

    private NoticeDetailResponse detail(Notice notice, String reader) {
        boolean admin = accessControlService.isAdminEmail(reader);
        List<NoticeComment> stored = noticeCommentRepository.findByNoticeIdOrderByIdAsc(notice.getId());
        Set<Long> answeredIds = new LinkedHashSet<>();
        stored.forEach(comment -> {
            if (comment.getParentId() != null) {
                answeredIds.add(comment.getParentId());
            }
        });
        // An emptied place whose replies are all gone has nothing left to hold together.
        List<NoticeComment> comments = stored.stream()
            .filter(comment -> !comment.isDeleted() || answeredIds.contains(comment.getId()))
            .toList();
        Map<Long, Long> commentLikes = noticeEngagementRepository.countCommentLikes(notice.getId());
        Set<Long> likedCommentIds = noticeEngagementRepository.findLikedCommentIds(notice.getId(), reader);
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
                    boolean deleted = comment.isDeleted();
                    boolean mine = !deleted && reader.equals(normalizeEmail(comment.getAuthorEmail()));
                    return new NoticeCommentResponse(
                        comment.getId(),
                        comment.getParentId(),
                        nicknames.get(normalizeEmail(comment.getAuthorEmail())),
                        comment.getContent(),
                        comment.getCreatedAt(),
                        comment.getEditedAt() != null,
                        deleted,
                        deleted ? 0L : commentLikes.getOrDefault(comment.getId(), 0L),
                        !deleted && likedCommentIds.contains(comment.getId()),
                        mine,
                        !deleted && (mine || admin)
                    );
                })
                .toList(),
            vote(notice, reader, admin),
            noticeEngagementRepository.countReads(List.of(notice.getId())).getOrDefault(notice.getId(), 0L),
            voteKept(notice, admin)
        );
    }

    /** A comment that is still there: an emptied place can be neither edited, removed nor liked. */
    private NoticeComment requireComment(Notice notice, Long commentId) {
        return noticeCommentRepository.findByIdAndNoticeId(commentId, notice.getId())
            .filter(comment -> !comment.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Comment not found"));
    }

    private static String requireCommentText(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 1~" + MAX_COMMENT_LENGTH + "자로 입력해 주세요.");
        }
        return text;
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
