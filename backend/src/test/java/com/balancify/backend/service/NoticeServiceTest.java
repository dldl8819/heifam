package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoticeServiceTest {

    private static final String ADMIN = "ops@hei.gg";
    private static final String MEMBER = "member@hei.gg";
    private static final String OTHER = "other@hei.gg";
    // A vote on how long a game may take, with five votes cast, and a vote for or against.
    private static final List<OptionRow> MINUTES = List.of(
        new OptionRow(21L, "30분", 0, 4), new OptionRow(22L, "25분", 1, 1), new OptionRow(23L, "24분", 2, 0)
    );
    private static final List<OptionRow> FOR_OR_AGAINST = List.of(
        new OptionRow(11L, "찬성", 0, 7), new OptionRow(12L, "반대", 1, 2)
    );

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private NoticeCommentRepository noticeCommentRepository;

    @Mock
    private NoticeEngagementRepository noticeEngagementRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private PointService pointService;

    @Mock
    private NoticeVoteRepository noticeVoteRepository;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(
            noticeRepository,
            noticeCommentRepository,
            noticeEngagementRepository,
            accessControlService,
            pointService,
            noticeVoteRepository,
            operationAuditLogService
        );
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(accessControlService.resolveDisplayNickname(ADMIN)).thenReturn("OpsUser");
        when(accessControlService.resolveDisplayNicknames(anyCollection()))
            .thenReturn(Map.of(ADMIN, "OpsUser", MEMBER, "YOUR_USERNAME", OTHER, "OtherUser"));
        when(noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(
            notice(3L, "admins only", true),
            notice(2L, "second", false),
            notice(1L, "first", false)
        ));
    }

    @Test
    void showsVisitorsOnlyTheTitlesMembersMaySee() {
        List<NoticeTitleResponse> titles = noticeService.listTitles(1L);

        assertThat(titles).extracting(NoticeTitleResponse::title).containsExactly("second", "first");
    }

    @Test
    void listsWhatAMemberHasReadAndCountsTheRest() {
        when(noticeEngagementRepository.findReadNoticeIds(MEMBER, List.of(2L, 1L))).thenReturn(Set.of(2L));
        when(noticeEngagementRepository.countLikes(List.of(2L, 1L))).thenReturn(Map.of(2L, 4L));

        NoticeListResponse list = noticeService.list(1L, " Member@hei.gg ");

        assertThat(list.notices()).extracting(NoticeListItemResponse::title, NoticeListItemResponse::read)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("second", true), org.assertj.core.groups.Tuple.tuple("first", false));
        assertThat(list.notices().getFirst().likeCount()).isEqualTo(4L);
        assertThat(list.notices().getFirst().authorNickname()).isEqualTo("OpsUser");
        assertThat(list.readCount()).isEqualTo(1);
        assertThat(list.unreadCount()).isEqualTo(1);
        assertThat(list.notices()).extracting(NoticeListItemResponse::revised).containsExactly(false, false);
    }

    @Test
    void countsAReAnnouncedNoticeAsUnreadUntilItIsReadAgain() {
        Notice announcedAgain = notice(2L, "second", false);
        announcedAgain.setRevision(1);
        announcedAgain.setRevisedAt(OffsetDateTime.parse("2026-10-05T03:00:00Z"));
        when(noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(1L))
            .thenReturn(List.of(announcedAgain, notice(1L, "first", false)));
        // The repository leaves out reads older than the latest revision.
        when(noticeEngagementRepository.findReadNoticeIds(MEMBER, List.of(2L, 1L))).thenReturn(Set.of(1L));

        NoticeListResponse list = noticeService.list(1L, MEMBER);

        assertThat(list.notices())
            .extracting(NoticeListItemResponse::title, NoticeListItemResponse::read, NoticeListItemResponse::revised)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("second", false, true),
                org.assertj.core.groups.Tuple.tuple("first", true, false)
            );
        assertThat(list.unreadCount()).isEqualTo(1);
        assertThat(list.readCount()).isEqualTo(1);
    }

    @Test
    void showsAdminsTheNoticesKeptToThem() {
        NoticeListResponse list = noticeService.list(1L, ADMIN);

        assertThat(list.notices()).extracting(NoticeListItemResponse::adminOnly).containsExactly(true, false, false);
        assertThat(list.unreadCount()).isEqualTo(3);
    }

    @Test
    void marksANoticeReadWhenOpened() {
        stubNotice(2L, false);
        when(noticeEngagementRepository.hasLiked(2L, MEMBER)).thenReturn(true);

        NoticeDetailResponse detail = noticeService.open(1L, 2L, MEMBER);

        verify(noticeEngagementRepository).markRead(eq(2L), eq(MEMBER), any(), isNull());
        assertThat(detail.content()).isEqualTo("content-2");
        assertThat(detail.likedByMe()).isTrue();
    }

    @Test
    void hidesAdminOnlyNoticesFromMembers() {
        stubNotice(3L, true);

        assertThatThrownBy(() -> noticeService.open(1L, 3L, MEMBER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.addComment(1L, 3L, MEMBER, "hi", null)).isInstanceOf(NoSuchElementException.class);
        verify(noticeEngagementRepository, never()).markRead(any(), any(), any(), any());
        assertThat(noticeService.open(1L, 3L, ADMIN).adminOnly()).isTrue();
    }

    @Test
    void savesTrimmedCommentsWithinTheLimit() {
        stubNotice(2L, false);

        noticeService.addComment(1L, 2L, MEMBER, "  좋아요  ", null);

        ArgumentCaptor<NoticeComment> saved = ArgumentCaptor.forClass(NoticeComment.class);
        verify(noticeCommentRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("좋아요");
        assertThat(saved.getValue().getAuthorEmail()).isEqualTo(MEMBER);
        assertThatThrownBy(() -> noticeService.addComment(1L, 2L, MEMBER, " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noticeService.addComment(1L, 2L, MEMBER, "x".repeat(501), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void letsWritersAndAdminsDeleteComments() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));
        when(noticeCommentRepository.findByNoticeIdOrderByIdAsc(2L)).thenReturn(List.of(comment));

        NoticeDetailResponse forAdmin = noticeService.open(1L, 2L, ADMIN);
        assertThat(forAdmin.comments().getFirst().canDelete()).isTrue();
        assertThat(forAdmin.comments().getFirst().mine()).isFalse();
        assertThatThrownBy(() -> noticeService.deleteComment(1L, 2L, 9L, "other@hei.gg"))
            .isInstanceOf(NoticeForbiddenException.class);

        noticeService.deleteComment(1L, 2L, 9L, MEMBER);
        noticeService.deleteComment(1L, 2L, 9L, ADMIN);
        verify(noticeCommentRepository, org.mockito.Mockito.times(2)).delete(comment);
    }

    @Test
    void givesAPointForReadingLikingAndCommentingOnANotice() {
        stubNotice(2L, false);

        noticeService.open(1L, 2L, MEMBER);
        noticeService.setLike(1L, 2L, MEMBER, true);
        noticeService.setLike(1L, 2L, MEMBER, false);
        noticeService.addComment(1L, 2L, MEMBER, "YOUR_COMMENT", null);

        verify(pointService).grantNoticeReadPoint(MEMBER, 2L, 0);
        verify(pointService).grantNoticePoint(MEMBER, 2L, PointService.REASON_NOTICE_LIKE);
        verify(pointService).grantNoticePoint(MEMBER, 2L, PointService.REASON_NOTICE_COMMENT);
        verify(pointService, org.mockito.Mockito.times(2)).grantNoticePoint(any(), any(), any());
    }

    @Test
    void offersTheFirstPostingsReadingPointOnEveryOpenAsBefore() {
        stubNotice(2L, false);
        when(noticeEngagementRepository.markRead(eq(2L), eq(MEMBER), any(), isNull())).thenReturn(true, false);

        noticeService.open(1L, 2L, MEMBER);
        noticeService.open(1L, 2L, MEMBER);

        // The ledger pays "notice:2" once; someone who read it before points opened still gets it.
        verify(pointService, org.mockito.Mockito.times(2)).grantNoticeReadPoint(MEMBER, 2L, 0);
    }

    @Test
    void paysAReAnnouncedNoticeOnceToThoseItTurnsReadFor() {
        OffsetDateTime revisedAt = OffsetDateTime.parse("2026-10-05T03:00:00Z");
        Notice notice = stubNotice(2L, false);
        notice.setRevision(2);
        notice.setRevisedAt(revisedAt);
        when(noticeEngagementRepository.markRead(eq(2L), eq(MEMBER), any(), eq(revisedAt))).thenReturn(true, false);

        noticeService.open(1L, 2L, MEMBER);
        noticeService.open(1L, 2L, MEMBER);

        verify(pointService).grantNoticeReadPoint(MEMBER, 2L, 2);
        verify(pointService, org.mockito.Mockito.times(1)).grantNoticeReadPoint(any(), any(), anyInt());
    }

    @Test
    void paysTheEditorNothingForTheRevisionTheyAnnounced() {
        OffsetDateTime revisedAt = OffsetDateTime.parse("2026-10-05T03:00:00Z");
        Notice notice = stubNotice(2L, false);
        notice.setRevision(1);
        notice.setRevisedAt(revisedAt);
        // Their read was saved with the edit, so opening the notice turns nothing read.
        when(noticeEngagementRepository.markRead(eq(2L), eq(ADMIN), any(), eq(revisedAt))).thenReturn(false);

        noticeService.open(1L, 2L, ADMIN);

        verify(pointService, never()).grantNoticeReadPoint(any(), any(), anyInt());
    }

    @Test
    void keepsLikesAndCommentsOncePerNoticeWhateverTheRevision() {
        Notice notice = stubNotice(2L, false);
        notice.setRevision(3);
        notice.setRevisedAt(OffsetDateTime.parse("2026-10-05T03:00:00Z"));

        noticeService.setLike(1L, 2L, MEMBER, true);
        noticeService.addComment(1L, 2L, MEMBER, "YOUR_COMMENT", null);

        verify(pointService).grantNoticePoint(MEMBER, 2L, PointService.REASON_NOTICE_LIKE);
        verify(pointService).grantNoticePoint(MEMBER, 2L, PointService.REASON_NOTICE_COMMENT);
        verify(pointService, never()).grantNoticeReadPoint(any(), any(), anyInt());
    }

    @Test
    void neverSavesAReadOlderThanTheRevisionItRead() {
        OffsetDateTime revisedAt = OffsetDateTime.now().plusMinutes(5);
        Notice notice = stubNotice(2L, false);
        notice.setRevision(1);
        notice.setRevisedAt(revisedAt);

        noticeService.open(1L, 2L, MEMBER);

        verify(noticeEngagementRepository).markRead(2L, MEMBER, revisedAt, revisedAt);
    }

    @Test
    void filesAReplyUnderTheCommentItAnswers() {
        stubNotice(2L, false);
        NoticeComment first = comment(9L, 2L, MEMBER);
        NoticeComment reply = comment(10L, 2L, ADMIN);
        reply.setParentId(9L);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(first));
        when(noticeCommentRepository.findByIdAndNoticeId(10L, 2L)).thenReturn(Optional.of(reply));

        noticeService.addComment(1L, 2L, OTHER, "to the comment", 9L);
        // Answering a reply joins the same thread: it is filed under the comment that reply answers.
        noticeService.addComment(1L, 2L, OTHER, "to the reply", 10L);
        noticeService.addComment(1L, 2L, OTHER, "to the notice", null);

        ArgumentCaptor<NoticeComment> saved = ArgumentCaptor.forClass(NoticeComment.class);
        verify(noticeCommentRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(NoticeComment::getParentId).containsExactly(9L, 9L, null);
        // A reply is a comment: the same point, once per notice.
        verify(pointService, times(3)).grantNoticePoint(OTHER, 2L, PointService.REASON_NOTICE_COMMENT);
    }

    @Test
    void refusesAReplyToACommentThatIsNotOnTheNotice() {
        stubNotice(2L, false);
        when(noticeCommentRepository.findByIdAndNoticeId(77L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.addComment(1L, 2L, MEMBER, "hello", 77L))
            .isInstanceOf(NoSuchElementException.class);
        verify(noticeCommentRepository, never()).save(any());
        verify(pointService, never()).grantNoticePoint(any(), any(), any());
    }

    @Test
    void letsOnlyTheWriterEditACommentAndMarksItEdited() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));
        when(noticeCommentRepository.findByNoticeIdOrderByIdAsc(2L)).thenReturn(List.of(comment));

        // Admins may remove a comment but not put words in its writer's mouth.
        assertThatThrownBy(() -> noticeService.editComment(1L, 2L, 9L, ADMIN, "reworded"))
            .isInstanceOf(NoticeForbiddenException.class);
        assertThatThrownBy(() -> noticeService.editComment(1L, 2L, 9L, MEMBER, " "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noticeService.editComment(1L, 2L, 9L, MEMBER, "x".repeat(501)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(comment.getContent()).isEqualTo("hello");

        // Saved as it was, it is not an edit.
        assertThat(noticeService.editComment(1L, 2L, 9L, MEMBER, " hello ").comments().getFirst().edited()).isFalse();
        verify(noticeCommentRepository, never()).save(any());

        NoticeDetailResponse edited = noticeService.editComment(1L, 2L, 9L, MEMBER, "  hello again ");
        assertThat(comment.getContent()).isEqualTo("hello again");
        assertThat(comment.getEditedAt()).isNotNull();
        assertThat(edited.comments().getFirst().edited()).isTrue();
        assertThat(edited.comments().getFirst().content()).isEqualTo("hello again");
        verify(pointService, never()).grantNoticePoint(any(), any(), any());
    }

    @Test
    void emptiesACommentThatHasRepliesInsteadOfRemovingIt() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        comment.setEditedAt(OffsetDateTime.now());
        NoticeComment reply = comment(10L, 2L, OTHER);
        reply.setParentId(9L);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));
        when(noticeCommentRepository.existsByParentId(9L)).thenReturn(true);
        when(noticeCommentRepository.findByNoticeIdOrderByIdAsc(2L)).thenReturn(List.of(comment, reply));
        when(noticeEngagementRepository.countCommentLikes(2L)).thenReturn(Map.of(9L, 4L, 10L, 1L));

        NoticeDetailResponse detail = noticeService.deleteComment(1L, 2L, 9L, MEMBER);

        verify(noticeCommentRepository, never()).delete(any(NoticeComment.class));
        verify(noticeCommentRepository).save(comment);
        verify(noticeEngagementRepository).clearCommentLikes(9L);
        // Nothing of the writer is left on it.
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isEmpty();
        assertThat(comment.getAuthorEmail()).isEmpty();
        assertThat(comment.getEditedAt()).isNull();
        assertThat(detail.comments())
            .extracting(
                NoticeCommentResponse::id, NoticeCommentResponse::parentId, NoticeCommentResponse::deleted,
                NoticeCommentResponse::authorNickname, NoticeCommentResponse::content, NoticeCommentResponse::likeCount,
                NoticeCommentResponse::mine, NoticeCommentResponse::canDelete
            )
            .containsExactly(
                tuple(9L, null, true, null, "", 0L, false, false),
                tuple(10L, 9L, false, "OtherUser", "hello", 1L, false, false)
            );

        // What is left can be answered, but not edited, removed or liked.
        assertThatThrownBy(() -> noticeService.editComment(1L, 2L, 9L, MEMBER, "back"))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.deleteComment(1L, 2L, 9L, ADMIN))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.setCommentLike(1L, 2L, 9L, OTHER, true))
            .isInstanceOf(NoSuchElementException.class);
        noticeService.addComment(1L, 2L, OTHER, "still talking", 9L);
        ArgumentCaptor<NoticeComment> saved = ArgumentCaptor.forClass(NoticeComment.class);
        verify(noticeCommentRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getParentId()).isEqualTo(9L);
    }

    @Test
    void removesAnEmptiedCommentWithItsLastReply() {
        stubNotice(2L, false);
        NoticeComment emptied = comment(9L, 2L, "");
        emptied.setContent("");
        emptied.setDeletedAt(OffsetDateTime.now());
        NoticeComment reply = comment(10L, 2L, OTHER);
        reply.setParentId(9L);
        NoticeComment lastReply = comment(11L, 2L, OTHER);
        lastReply.setParentId(9L);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(emptied));
        when(noticeCommentRepository.findByIdAndNoticeId(10L, 2L)).thenReturn(Optional.of(reply));
        when(noticeCommentRepository.findByIdAndNoticeId(11L, 2L)).thenReturn(Optional.of(lastReply));
        // The emptied comment still has a reply after the first removal, none after the second.
        when(noticeCommentRepository.existsByParentId(9L)).thenReturn(true, false);

        noticeService.deleteComment(1L, 2L, 10L, OTHER);
        verify(noticeCommentRepository).delete(reply);
        verify(noticeCommentRepository, never()).delete(emptied);

        noticeService.deleteComment(1L, 2L, 11L, OTHER);
        verify(noticeCommentRepository).delete(lastReply);
        verify(noticeCommentRepository).delete(emptied);
    }

    @Test
    void keepsACommentThatIsNotEmptiedWhenItsLastReplyGoes() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        NoticeComment reply = comment(10L, 2L, OTHER);
        reply.setParentId(9L);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));
        when(noticeCommentRepository.findByIdAndNoticeId(10L, 2L)).thenReturn(Optional.of(reply));

        noticeService.deleteComment(1L, 2L, 10L, OTHER);

        verify(noticeCommentRepository).delete(reply);
        verify(noticeCommentRepository, never()).delete(comment);
    }

    @Test
    void showsNothingOfAnEmptiedCommentOnceItHasNoReplies() {
        stubNotice(2L, false);
        NoticeComment emptied = comment(9L, 2L, "");
        emptied.setDeletedAt(OffsetDateTime.now());
        NoticeComment other = comment(12L, 2L, MEMBER);
        when(noticeCommentRepository.findByNoticeIdOrderByIdAsc(2L)).thenReturn(List.of(emptied, other));

        assertThat(noticeService.open(1L, 2L, MEMBER).comments())
            .extracting(NoticeCommentResponse::id)
            .containsExactly(12L);
    }

    @Test
    void likesSomeoneElsesCommentAndPaysThePointForIt() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        NoticeComment reply = comment(10L, 2L, ADMIN);
        reply.setParentId(9L);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));
        when(noticeCommentRepository.findByIdAndNoticeId(10L, 2L)).thenReturn(Optional.of(reply));
        when(noticeCommentRepository.findByNoticeIdOrderByIdAsc(2L)).thenReturn(List.of(comment, reply));
        when(noticeEngagementRepository.countCommentLikes(2L)).thenReturn(Map.of(9L, 2L));
        when(noticeEngagementRepository.findLikedCommentIds(2L, OTHER)).thenReturn(Set.of(9L));

        NoticeDetailResponse detail = noticeService.setCommentLike(1L, 2L, 9L, OTHER, true);
        noticeService.setCommentLike(1L, 2L, 10L, OTHER, true);

        verify(noticeEngagementRepository).likeComment(9L, OTHER);
        verify(noticeEngagementRepository).likeComment(10L, OTHER);
        verify(pointService).grantNoticeCommentLikePoint(OTHER, 9L);
        verify(pointService).grantNoticeCommentLikePoint(OTHER, 10L);
        assertThat(detail.comments())
            .extracting(NoticeCommentResponse::id, NoticeCommentResponse::likeCount, NoticeCommentResponse::likedByMe)
            .containsExactly(tuple(9L, 2L, true), tuple(10L, 0L, false));
    }

    @Test
    void refusesALikeOnOnesOwnComment() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> noticeService.setCommentLike(1L, 2L, 9L, MEMBER, true))
            .isInstanceOf(NoticeForbiddenException.class);

        verify(noticeEngagementRepository, never()).likeComment(any(), any());
        verify(pointService, never()).grantNoticeCommentLikePoint(any(), any());
    }

    @Test
    void takesALikeBackWithoutTouchingPoints() {
        stubNotice(2L, false);
        NoticeComment comment = comment(9L, 2L, MEMBER);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 2L)).thenReturn(Optional.of(comment));

        noticeService.setCommentLike(1L, 2L, 9L, OTHER, false);

        verify(noticeEngagementRepository).unlikeComment(9L, OTHER);
        verify(noticeEngagementRepository, never()).likeComment(any(), any());
        verify(pointService, never()).grantNoticeCommentLikePoint(any(), any());
    }

    @Test
    void keepsCommentsOfAnAdminOnlyNoticeFromMembers() {
        stubNotice(3L, true);
        NoticeComment comment = comment(9L, 3L, ADMIN);
        when(noticeCommentRepository.findByIdAndNoticeId(9L, 3L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> noticeService.addComment(1L, 3L, MEMBER, "reply", 9L))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.editComment(1L, 3L, 9L, MEMBER, "edit"))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.setCommentLike(1L, 3L, 9L, MEMBER, true))
            .isInstanceOf(NoSuchElementException.class);
        verify(noticeEngagementRepository, never()).likeComment(any(), any());
        verify(pointService, never()).grantNoticeCommentLikePoint(any(), any());
    }

    @Test
    void showsNoVoteOnANoticeThatAsksForNone() {
        stubNotice(2L, false);

        assertThat(noticeService.open(1L, 2L, MEMBER).vote()).isNull();
        assertThat(noticeService.list(1L, MEMBER).notices()).extracting(NoticeListItemResponse::voteOpen).containsOnly(false);
        assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, 21L, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.withdrawVote(1L, 2L, MEMBER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, ADMIN, "26분")).isInstanceOf(NoSuchElementException.class);
        verify(noticeVoteRepository, never()).castVote(any(), any(), any());
        verify(noticeVoteRepository, never()).withdrawVote(any(), any());
        verify(noticeVoteRepository, never()).addOption(any(), any(), any());
    }

    @Test
    void showsTheOptionsWithTheirCountsAndTellsEachReaderTheirOwnChoice() {
        stubVote(2L, "OPEN", MINUTES);
        when(noticeVoteRepository.findVotedOptionId(2L, MEMBER)).thenReturn(Optional.of(22L));

        NoticeVoteResponse mine = noticeService.open(1L, 2L, MEMBER).vote();
        NoticeVoteResponse others = noticeService.open(1L, 2L, OTHER).vote();

        assertThat(mine.status()).isEqualTo("OPEN");
        assertThat(mine.totalVoters()).isEqualTo(5L);
        assertThat(mine.myOptionId()).isEqualTo(22L);
        assertThat(mine.options())
            .extracting(
                NoticeVoteOptionResponse::id, NoticeVoteOptionResponse::label, NoticeVoteOptionResponse::count,
                NoticeVoteOptionResponse::mine, NoticeVoteOptionResponse::voters
            )
            .containsExactly(
                tuple(21L, "30분", 4L, false, null),
                tuple(22L, "25분", 1L, true, null),
                tuple(23L, "24분", 0L, false, null)
            );
        assertThat(others.myOptionId()).isNull();
        assertThat(others.options()).extracting(NoticeVoteOptionResponse::mine).containsOnly(false);
        // Anonymous unless its writer said otherwise, closed to additions, and a member changes no option.
        assertThat(mine.anonymous()).isTrue();
        assertThat(mine.allowAdditions()).isFalse();
        assertThat(mine.canAddOption()).isFalse();
        assertThat(mine.canRemoveOptions()).isFalse();
        // Not a vote for or against: an older page reads nothing into it.
        assertThat(mine.agreeCount()).isZero();
        assertThat(mine.disagreeCount()).isZero();
        assertThat(mine.myChoice()).isNull();
    }

    @Test
    void readsAVoteForOrAgainstAsAnOlderPageDoes() {
        stubVote(2L, "OPEN", FOR_OR_AGAINST);
        when(noticeVoteRepository.findVotedOptionId(2L, MEMBER)).thenReturn(Optional.of(12L));

        NoticeVoteResponse mine = noticeService.open(1L, 2L, MEMBER).vote();
        NoticeVoteResponse others = noticeService.open(1L, 2L, OTHER).vote();

        assertThat(mine.agreeCount()).isEqualTo(7L);
        assertThat(mine.disagreeCount()).isEqualTo(2L);
        assertThat(mine.myChoice()).isEqualTo("DISAGREE");
        assertThat(others.myChoice()).isNull();
        assertThat(mine.options()).extracting(NoticeVoteOptionResponse::label).containsExactly("찬성", "반대");
    }

    @Test
    void neverLooksUpWhoVotedOnAnAnonymousVote() {
        stubVote(2L, "OPEN", MINUTES);

        assertThat(noticeService.open(1L, 2L, ADMIN).vote().options())
            .extracting(NoticeVoteOptionResponse::voters)
            .containsOnlyNulls();

        verify(noticeVoteRepository, never()).listVoters(any());
    }

    @Test
    void listsTheVotersUnderEachOptionOfANamedVote() {
        Notice notice = stubVote(2L, "OPEN", MINUTES);
        notice.setVoteAnonymous(false);
        when(noticeVoteRepository.listVoters(2L)).thenReturn(List.of(
            new VoterRow(21L, MEMBER), new VoterRow(21L, "nameless@hei.gg"), new VoterRow(22L, " Other@Hei.gg ")
        ));

        NoticeVoteResponse vote = noticeService.open(1L, 2L, MEMBER).vote();

        assertThat(vote.anonymous()).isFalse();
        assertThat(vote.options().get(0).voters()).containsExactly("YOUR_USERNAME", null);
        assertThat(vote.options().get(1).voters()).containsExactly("OtherUser");
        assertThat(vote.options().get(2).voters()).isEmpty();
    }

    @Test
    void castsMovesAndTakesBackAVoteWhileTheVoteIsOpen() {
        stubVote(2L, "OPEN", MINUTES);

        noticeService.castVote(1L, 2L, MEMBER, 21L, null);
        noticeService.castVote(1L, 2L, MEMBER, 23L, null);
        noticeService.withdrawVote(1L, 2L, MEMBER);

        verify(noticeVoteRepository).castVote(2L, MEMBER, 21L);
        verify(noticeVoteRepository).castVote(2L, MEMBER, 23L);
        verify(noticeVoteRepository).withdrawVote(2L, MEMBER);
        // Read with the lock an edit waits for, so closing the vote and a late vote never cross.
        verify(noticeRepository, times(3)).findByIdAndGroupIdForShare(2L, 1L);
        // Voting is not paid.
        verify(pointService, never()).grantNoticePoint(any(), any(), any());
    }

    @Test
    void takesForOrAgainstFromAnOlderPage() {
        stubVote(2L, "OPEN", FOR_OR_AGAINST);

        noticeService.castVote(1L, 2L, MEMBER, null, " agree ");
        noticeService.castVote(1L, 2L, MEMBER, null, "DISAGREE");

        verify(noticeVoteRepository).castVote(2L, MEMBER, 11L);
        verify(noticeVoteRepository).castVote(2L, MEMBER, 12L);
    }

    @Test
    void refusesAVoteThatNamesNoOptionOfTheNotice() {
        stubVote(2L, "OPEN", MINUTES);

        // Neither an option nor a word an older page sends.
        for (String choice : new String[] {null, "", "ABSTAIN", "yes"}) {
            assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, null, choice))
                .isInstanceOf(IllegalArgumentException.class);
        }
        // For or against, on a vote that has no such options.
        assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, null, "AGREE"))
            .isInstanceOf(IllegalArgumentException.class);
        // An option that was taken away, or is another notice's: the page is told to read the vote again.
        assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, 99L, null))
            .isInstanceOf(NoticeVoteConflictException.class);
        verify(noticeVoteRepository, never()).castVote(any(), any(), any());
    }

    @Test
    void keepsTheResultButChangesNothingOnceTheVoteIsClosed() {
        Notice notice = stubVote(2L, "CLOSED", MINUTES);
        notice.setVoteAllowAdditions(true);
        when(noticeVoteRepository.findVotedOptionId(2L, MEMBER)).thenReturn(Optional.of(21L));

        NoticeVoteResponse vote = noticeService.open(1L, 2L, ADMIN).vote();
        assertThat(vote.status()).isEqualTo("CLOSED");
        assertThat(vote.totalVoters()).isEqualTo(5L);
        assertThat(vote.canAddOption()).isFalse();
        assertThat(vote.canRemoveOptions()).isFalse();
        assertThat(noticeService.open(1L, 2L, MEMBER).vote().myOptionId()).isEqualTo(21L);
        assertThat(noticeService.list(1L, MEMBER).notices()).extracting(NoticeListItemResponse::voteOpen).containsOnly(false);

        assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, 22L, null))
            .isInstanceOf(NoticeVoteClosedException.class);
        assertThatThrownBy(() -> noticeService.withdrawVote(1L, 2L, MEMBER))
            .isInstanceOf(NoticeVoteClosedException.class);
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, MEMBER, "26분"))
            .isInstanceOf(NoticeVoteClosedException.class);
        assertThatThrownBy(() -> noticeService.removeVoteOption(1L, 2L, 21L, ADMIN))
            .isInstanceOf(NoticeVoteClosedException.class);
        verify(noticeVoteRepository, never()).castVote(any(), any(), any());
        verify(noticeVoteRepository, never()).withdrawVote(any(), any());
        verify(noticeVoteRepository, never()).addOption(any(), any(), any());
        verify(noticeVoteRepository, never()).removeOption(any(), any());
    }

    @Test
    void keepsTheVoteOfANoticeForAdminsFromMembers() {
        Notice notice = stubNotice(3L, true);
        notice.setVoteStatus("OPEN");
        notice.setVoteAllowAdditions(true);
        when(noticeVoteRepository.listOptions(3L)).thenReturn(MINUTES);

        assertThatThrownBy(() -> noticeService.castVote(1L, 3L, MEMBER, 21L, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.withdrawVote(1L, 3L, MEMBER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 3L, MEMBER, "26분")).isInstanceOf(NoSuchElementException.class);
        verify(noticeVoteRepository, never()).castVote(any(), any(), any());
        verify(noticeVoteRepository, never()).addOption(any(), any(), any());

        noticeService.castVote(1L, 3L, ADMIN, 21L, null);
        verify(noticeVoteRepository).castVote(3L, ADMIN, 21L);
    }

    @Test
    void letsVotersAddAnOptionOnlyWhenTheVoteAllowsItAndAdminsAlways() {
        Notice notice = stubVote(2L, "OPEN", MINUTES);

        assertThat(noticeService.open(1L, 2L, MEMBER).vote().canAddOption()).isFalse();
        assertThat(noticeService.open(1L, 2L, ADMIN).vote().canAddOption()).isTrue();
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, MEMBER, "26분"))
            .isInstanceOf(NoticeForbiddenException.class);
        verify(noticeVoteRepository, never()).addOption(any(), any(), any());
        noticeService.addVoteOption(1L, 2L, ADMIN, "26분");
        verify(noticeVoteRepository).addOption(2L, "26분", ADMIN);

        notice.setVoteAllowAdditions(true);
        assertThat(noticeService.open(1L, 2L, MEMBER).vote().canAddOption()).isTrue();
        assertThat(noticeService.open(1L, 2L, MEMBER).vote().allowAdditions()).isTrue();
        noticeService.addVoteOption(1L, 2L, " Member@Hei.gg ", "  27분  ");
        verify(noticeVoteRepository).addOption(2L, "27분", MEMBER);
        // Locked as an edit locks it, so two additions at once come one after the other.
        verify(noticeRepository, times(3)).findByIdAndGroupIdForUpdate(2L, 1L);
    }

    @Test
    void refusesAnAddedOptionThatIsEmptyTooLongOrAlreadyThere() {
        Notice notice = stubVote(2L, "OPEN", MINUTES);
        notice.setVoteAllowAdditions(true);

        for (String label : new String[] {null, "   ", "x".repeat(NoticeVotes.MAX_OPTION_LENGTH + 1)}) {
            assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, MEMBER, label))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, MEMBER, " 30분 "))
            .isInstanceOf(NoticeVoteConflictException.class);
        when(noticeVoteRepository.listOptions(2L)).thenReturn(List.of(new OptionRow(31L, "Yes", 0, 0), new OptionRow(32L, "No", 1, 0)));
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, MEMBER, "yes"))
            .isInstanceOf(NoticeVoteConflictException.class);
        verify(noticeVoteRepository, never()).addOption(any(), any(), any());

        // What the check above let through but the database holds to be the same option.
        when(noticeVoteRepository.addOption(2L, "Maybe", MEMBER)).thenThrow(new DuplicateKeyException("uq_notice_vote_options_label"));
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, MEMBER, "Maybe"))
            .isInstanceOf(NoticeVoteConflictException.class);
    }

    @Test
    void holdsNoMoreOptionsThanAVoteMay() {
        List<OptionRow> full = new ArrayList<>();
        for (int index = 0; index < NoticeVotes.MAX_OPTIONS; index++) {
            full.add(new OptionRow(100L + index, "option " + index, index, 0));
        }
        Notice notice = stubVote(2L, "OPEN", full);
        notice.setVoteAllowAdditions(true);

        assertThat(noticeService.open(1L, 2L, MEMBER).vote().canAddOption()).isFalse();
        assertThat(noticeService.open(1L, 2L, ADMIN).vote().canAddOption()).isFalse();
        assertThatThrownBy(() -> noticeService.addVoteOption(1L, 2L, ADMIN, "one more"))
            .isInstanceOf(NoticeVoteConflictException.class);
        verify(noticeVoteRepository, never()).addOption(any(), any(), any());
    }

    @Test
    void letsOnlyAdminsTakeAnOptionAwayAndLogsHowManyVotesWentWithIt() {
        Notice notice = stubVote(2L, "OPEN", MINUTES);

        assertThat(noticeService.open(1L, 2L, MEMBER).vote().canRemoveOptions()).isFalse();
        assertThat(noticeService.open(1L, 2L, ADMIN).vote().canRemoveOptions()).isTrue();
        assertThatThrownBy(() -> noticeService.removeVoteOption(1L, 2L, 21L, MEMBER))
            .isInstanceOf(NoticeForbiddenException.class);
        assertThatThrownBy(() -> noticeService.removeVoteOption(1L, 2L, 99L, ADMIN))
            .isInstanceOf(NoticeVoteConflictException.class);
        verify(noticeVoteRepository, never()).removeOption(any(), any());

        noticeService.removeVoteOption(1L, 2L, 21L, " Ops@Hei.gg ");

        verify(noticeVoteRepository).removeOption(2L, 21L);
        verify(operationAuditLogService).recordNoticeVoteOptionRemoved(ADMIN, "OpsUser", 1L, notice, "30분", 4L);
    }

    @Test
    void keepsAtLeastTwoOptionsOnAVote() {
        stubVote(2L, "OPEN", FOR_OR_AGAINST);

        assertThat(noticeService.open(1L, 2L, ADMIN).vote().canRemoveOptions()).isFalse();
        assertThatThrownBy(() -> noticeService.removeVoteOption(1L, 2L, 11L, ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
        verify(noticeVoteRepository, never()).removeOption(any(), any());
    }

    @Test
    void showsOnlyAdminsAVoteThatWasTakenOffTheNoticeAndIsKept() {
        Notice notice = stubVote(2L, "NONE", MINUTES);
        notice.setVoteAnonymous(false);
        notice.setVoteAllowAdditions(true);

        NoticeDetailResponse forAdmin = noticeService.open(1L, 2L, ADMIN);
        NoticeDetailResponse forMember = noticeService.open(1L, 2L, MEMBER);

        assertThat(forAdmin.vote()).isNull();
        assertThat(forAdmin.voteKept()).isEqualTo(new NoticeVoteKeptResponse(List.of("30분", "25분", "24분"), 5L, false, true));
        assertThat(forMember.vote()).isNull();
        assertThat(forMember.voteKept()).isNull();

        // While the notice shows its vote there is nothing kept aside, and a notice that never had one has none.
        notice.setVoteStatus("OPEN");
        assertThat(noticeService.open(1L, 2L, ADMIN).voteKept()).isNull();
        notice.setVoteStatus("NONE");
        when(noticeVoteRepository.listOptions(2L)).thenReturn(List.of());
        assertThat(noticeService.open(1L, 2L, ADMIN).voteKept()).isNull();
    }

    @Test
    void showsHowManyPeopleOpenedEachNoticeAndVotedOnThoseThatAsk() {
        when(noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(1L)).thenAnswer(invocation -> {
            Notice open = notice(3L, "third", false);
            open.setVoteStatus("OPEN");
            Notice closed = notice(2L, "second", false);
            closed.setVoteStatus("CLOSED");
            // Its vote was taken off again; the votes cast are kept but no longer shown.
            Notice plain = notice(1L, "first", false);
            return List.of(open, closed, plain);
        });
        when(noticeEngagementRepository.countReads(List.of(3L, 2L, 1L))).thenReturn(Map.of(3L, 12L, 1L, 5L));
        when(noticeEngagementRepository.countVoters(List.of(3L, 2L, 1L))).thenReturn(Map.of(3L, 9L, 1L, 4L));

        assertThat(noticeService.list(1L, MEMBER).notices())
            .extracting(NoticeListItemResponse::id, NoticeListItemResponse::viewCount, NoticeListItemResponse::voteCount)
            .containsExactly(tuple(3L, 12L, 9L), tuple(2L, 0L, 0L), tuple(1L, 5L, null));
    }

    @Test
    void countsTheReaderAmongThoseWhoOpenedTheNotice() {
        when(noticeRepository.findByIdAndGroupIdForShare(2L, 1L)).thenReturn(Optional.of(notice(2L, "second", false)));
        when(noticeEngagementRepository.countReads(List.of(2L))).thenReturn(Map.of(2L, 31L));

        assertThat(noticeService.open(1L, 2L, MEMBER).viewCount()).isEqualTo(31L);
    }

    @Test
    void marksNoticesWithAnOpenVoteInTheList() {
        // The list in setUp holds notices 3 (admins only), 2 and 1.
        when(noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(1L)).thenAnswer(invocation -> {
            Notice open = notice(2L, "second", false);
            open.setVoteStatus("OPEN");
            Notice closed = notice(1L, "first", false);
            closed.setVoteStatus("CLOSED");
            return List.of(open, closed);
        });

        assertThat(noticeService.list(1L, MEMBER).notices())
            .extracting(NoticeListItemResponse::id, NoticeListItemResponse::voteOpen)
            .containsExactly(tuple(2L, true), tuple(1L, false));
    }

    @Test
    void likesAndUnlikes() {
        stubNotice(2L, false);

        noticeService.setLike(1L, 2L, MEMBER, true);
        noticeService.setLike(1L, 2L, MEMBER, false);

        verify(noticeEngagementRepository).like(2L, MEMBER);
        verify(noticeEngagementRepository).unlike(2L, MEMBER);
    }

    private Notice stubNotice(Long id, boolean adminOnly) {
        Notice notice = notice(id, "notice " + id, adminOnly);
        when(noticeRepository.findByIdAndGroupId(id, 1L)).thenReturn(Optional.of(notice));
        when(noticeRepository.findByIdAndGroupIdForShare(id, 1L)).thenReturn(Optional.of(notice));
        when(noticeRepository.findByIdAndGroupIdForUpdate(id, 1L)).thenReturn(Optional.of(notice));
        return notice;
    }

    /** A notice members may open, with a vote in the given state on the given options. */
    private Notice stubVote(Long id, String status, List<OptionRow> options) {
        Notice notice = stubNotice(id, false);
        notice.setVoteStatus(status);
        when(noticeVoteRepository.listOptions(id)).thenReturn(options);
        return notice;
    }

    private Notice notice(Long id, String title, boolean adminOnly) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setGroupId(1L);
        notice.setTitle(title);
        notice.setContent("content-" + id);
        notice.setAuthorEmail(ADMIN);
        notice.setAdminOnly(adminOnly);
        return notice;
    }

    private NoticeComment comment(Long id, Long noticeId, String author) {
        NoticeComment comment = new NoticeComment();
        ReflectionTestUtils.setField(comment, "id", id);
        comment.setNoticeId(noticeId);
        comment.setAuthorEmail(author);
        comment.setContent("hello");
        return comment;
    }
}
