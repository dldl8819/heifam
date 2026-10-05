package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            .thenReturn(Map.of(ADMIN, "OpsUser", MEMBER, "YOUR_USERNAME"));
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
        assertThatThrownBy(() -> noticeService.addComment(1L, 3L, MEMBER, "hi")).isInstanceOf(NoSuchElementException.class);
        verify(noticeEngagementRepository, never()).markRead(any(), any(), any(), any());
        assertThat(noticeService.open(1L, 3L, ADMIN).adminOnly()).isTrue();
    }

    @Test
    void savesTrimmedCommentsWithinTheLimit() {
        stubNotice(2L, false);

        noticeService.addComment(1L, 2L, MEMBER, "  좋아요  ");

        ArgumentCaptor<NoticeComment> saved = ArgumentCaptor.forClass(NoticeComment.class);
        verify(noticeCommentRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("좋아요");
        assertThat(saved.getValue().getAuthorEmail()).isEqualTo(MEMBER);
        assertThatThrownBy(() -> noticeService.addComment(1L, 2L, MEMBER, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noticeService.addComment(1L, 2L, MEMBER, "x".repeat(501)))
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
        noticeService.addComment(1L, 2L, MEMBER, "YOUR_COMMENT");

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
        noticeService.addComment(1L, 2L, MEMBER, "YOUR_COMMENT");

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
