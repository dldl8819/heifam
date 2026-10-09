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
import com.balancify.backend.api.group.dto.NoticeVoteResponse;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.domain.NoticeComment;
import com.balancify.backend.repository.NoticeCommentRepository;
import com.balancify.backend.repository.NoticeEngagementRepository;
import com.balancify.backend.repository.NoticeRepository;
import com.balancify.backend.service.exception.NoticeForbiddenException;
import com.balancify.backend.service.exception.NoticeVoteClosedException;
import java.time.OffsetDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoticeServiceTest {

    private static final String ADMIN = "ops@hei.gg";
    private static final String MEMBER = "member@hei.gg";
    private static final String OTHER = "other@hei.gg";

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

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(
            noticeRepository,
            noticeCommentRepository,
            noticeEngagementRepository,
            accessControlService,
            pointService
        );
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
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
        assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, "AGREE")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.withdrawVote(1L, 2L, MEMBER)).isInstanceOf(NoSuchElementException.class);
        verify(noticeEngagementRepository, never()).castVote(any(), any(), any());
        verify(noticeEngagementRepository, never()).withdrawVote(any(), any());
    }

    @Test
    void countsVotesAndTellsEachReaderOnlyTheirOwnChoice() {
        Notice notice = stubNotice(2L, false);
        notice.setVoteStatus("OPEN");
        when(noticeEngagementRepository.countVotes(2L)).thenReturn(Map.of("AGREE", 7L, "DISAGREE", 2L));
        when(noticeEngagementRepository.findVote(2L, MEMBER)).thenReturn("DISAGREE");

        NoticeVoteResponse mine = noticeService.open(1L, 2L, MEMBER).vote();
        NoticeVoteResponse others = noticeService.open(1L, 2L, OTHER).vote();

        assertThat(mine).isEqualTo(new NoticeVoteResponse("OPEN", 7L, 2L, "DISAGREE"));
        assertThat(others).isEqualTo(new NoticeVoteResponse("OPEN", 7L, 2L, null));
    }

    @Test
    void castsChangesAndTakesBackAVoteWhileTheVoteIsOpen() {
        Notice notice = stubNotice(2L, false);
        notice.setVoteStatus("OPEN");

        noticeService.castVote(1L, 2L, MEMBER, " agree ");
        noticeService.castVote(1L, 2L, MEMBER, "DISAGREE");
        noticeService.withdrawVote(1L, 2L, MEMBER);

        verify(noticeEngagementRepository).castVote(2L, MEMBER, "AGREE");
        verify(noticeEngagementRepository).castVote(2L, MEMBER, "DISAGREE");
        verify(noticeEngagementRepository).withdrawVote(2L, MEMBER);
        // Read with the lock an edit waits for, so closing the vote and a late vote never cross.
        verify(noticeRepository, times(3)).findByIdAndGroupIdForShare(2L, 1L);
        // Voting is not paid.
        verify(pointService, never()).grantNoticePoint(any(), any(), any());
    }

    @Test
    void refusesAChoiceThatIsNeitherForNorAgainst() {
        Notice notice = stubNotice(2L, false);
        notice.setVoteStatus("OPEN");

        for (String choice : new String[] {null, "", "ABSTAIN", "yes"}) {
            assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, choice))
                .isInstanceOf(IllegalArgumentException.class);
        }
        verify(noticeEngagementRepository, never()).castVote(any(), any(), any());
    }

    @Test
    void keepsTheResultButTakesNoMoreVotesOnceTheVoteIsClosed() {
        Notice notice = stubNotice(2L, false);
        notice.setVoteStatus("CLOSED");
        when(noticeEngagementRepository.countVotes(2L)).thenReturn(Map.of("AGREE", 3L));
        when(noticeEngagementRepository.findVote(2L, MEMBER)).thenReturn("AGREE");

        assertThat(noticeService.open(1L, 2L, MEMBER).vote()).isEqualTo(new NoticeVoteResponse("CLOSED", 3L, 0L, "AGREE"));
        assertThat(noticeService.list(1L, MEMBER).notices()).extracting(NoticeListItemResponse::voteOpen).containsOnly(false);
        assertThatThrownBy(() -> noticeService.castVote(1L, 2L, MEMBER, "DISAGREE"))
            .isInstanceOf(NoticeVoteClosedException.class);
        assertThatThrownBy(() -> noticeService.withdrawVote(1L, 2L, MEMBER))
            .isInstanceOf(NoticeVoteClosedException.class);
        verify(noticeEngagementRepository, never()).castVote(any(), any(), any());
        verify(noticeEngagementRepository, never()).withdrawVote(any(), any());
    }

    @Test
    void keepsTheVoteOfANoticeForAdminsFromMembers() {
        Notice notice = stubNotice(3L, true);
        notice.setVoteStatus("OPEN");

        assertThatThrownBy(() -> noticeService.castVote(1L, 3L, MEMBER, "AGREE")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> noticeService.withdrawVote(1L, 3L, MEMBER)).isInstanceOf(NoSuchElementException.class);
        verify(noticeEngagementRepository, never()).castVote(any(), any(), any());

        noticeService.castVote(1L, 3L, ADMIN, "AGREE");
        verify(noticeEngagementRepository).castVote(3L, ADMIN, "AGREE");
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
